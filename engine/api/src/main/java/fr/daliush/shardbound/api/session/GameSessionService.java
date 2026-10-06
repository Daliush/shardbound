package fr.daliush.shardbound.api.session;

import fr.daliush.shardbound.api.session.Rejection.Reason;
import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.Bot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckId;
import fr.daliush.shardbound.core.content.DeckValidator;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.text.ActionDescriber;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates, joins and plays games (spec §13.4). Every change loads the session, applies it and saves it with an
 * optimistic lock, so any instance can process any message; a conflict means another instance moved first.
 */
public final class GameSessionService {

    /** Guards against a bot loop that never gives the decision back. */
    static final int MAX_BOT_MOVES = 10_000;

    private static final Logger LOG = LoggerFactory.getLogger(GameSessionService.class);

    private final Content content;
    private final GameEngine engine;
    private final BotRoster bots;
    private final GameRepository repository;
    private final GameUpdates updates;
    private final Clock clock;
    private final Settings settings;
    private final SecureRandom random = new SecureRandom();
    private final SeatTokens tokens = new SeatTokens(random);
    private final GameLocks locks = new GameLocks();
    private final SessionMessages messages;
    private final ActionDescriber labels;

    /** {@code instance} names this server instance in the logs. */
    public record Settings(Duration botStepDelay, String instance) {}

    public GameSessionService(Content content, GameEngine engine, BotRoster bots, GameRepository repository,
                              GameUpdates updates, Clock clock, Settings settings) {
        this.content = content;
        this.engine = engine;
        this.bots = bots;
        this.repository = repository;
        this.updates = updates;
        this.clock = clock;
        this.settings = settings;
        this.messages = new SessionMessages(content, engine);
        this.labels = new ActionDescriber(engine.catalog());
    }

    /** The creator takes seat P1. A game against a bot starts at once; one against a human waits for the join. */
    public SeatAccess create(NewGame request) {
        Deck deck = legalDeck(request.deck());
        long seed = request.seed().orElseGet(random::nextLong);
        GameId id = GameId.random();
        String token = tokens.generate();
        Seat.Human creator = new Seat.Human(PlayerId.P1, deck.id(), SeatTokens.hash(token));
        return switch (request.opponent()) {
            case NewGame.Opponent.Bot bot -> {
                if (!bots.has(bot.name())) {
                    throw new SessionException.InvalidRequest("Unknown bot: " + bot.name());
                }
                Deck botDeck = legalDeck(bot.deck());
                Seat.Bot seat = new Seat.Bot(PlayerId.P2, botDeck.id(), bot.name(), bots.firstState(seed));
                repository.create(GameSession.started(id, seed, creator, seat, setUp(deck, botDeck, seed),
                        clock.instant()));
                resumeBots(id);
                yield new SeatAccess(id, token, Optional.empty());
            }
            case NewGame.Opponent.Human ignored -> {
                String joinCode = tokens.generate();
                repository.create(GameSession.waiting(id, seed, creator,
                        new Seat.Open(PlayerId.P2, SeatTokens.hash(joinCode)), clock.instant()));
                yield new SeatAccess(id, token, Optional.of(joinCode));
            }
        };
    }

    /** The joiner takes seat P2 and the game is set up: version 1, sent to the creator as an update. */
    public SeatAccess join(GameId id, String joinCode, String deckId) {
        Deck deck = legalDeck(deckId);
        return locks.call(id, () -> {
            while (true) {
                GameSession session = load(id);
                if (!(session.p2() instanceof Seat.Open open) || !SeatTokens.matches(joinCode, open.joinCodeHash())) {
                    throw new SessionException.JoinRefused();
                }
                String token = tokens.generate();
                Seat.Human joiner = new Seat.Human(PlayerId.P2, deck.id(), SeatTokens.hash(token));
                Transition setup = setUp(content.deck(deckOf(session.p1()).value()), deck, session.seed());
                if (save(session.version(), withUpdates(session.joined(joiner, setup, clock.instant()),
                        setup.events()))) {
                    return new SeatAccess(id, token, Optional.empty());
                }
            }
        });
    }

    /** The seat that {@code token} holds in the game. */
    public PlayerId authenticate(GameId id, String token) {
        GameSession session = load(id);
        return Stream.of(session.p1(), session.p2())
                .filter(seat -> seat instanceof Seat.Human human && SeatTokens.matches(token, human.tokenHash()))
                .map(Seat::player)
                .findFirst()
                .orElseThrow(SessionException.InvalidToken::new);
    }

    public Optional<GameSession> find(GameId id) {
        return repository.find(id);
    }

    public SeatState state(GameId id, PlayerId seat) {
        GameSession session = load(id);
        return new SeatState(session.version(), state(session, seat));
    }

    public String state(GameSession session, PlayerId seat) {
        return messages.state(session, seat);
    }

    /**
     * Applies the seat's answer to the pending decision, then lets the bots play. The decision is recomputed
     * from the saved state: the client is never trusted.
     */
    public Optional<Rejection> act(GameId id, PlayerId seat, String decisionId, int actionIndex) {
        return locks.call(id, () -> {
            while (true) {
                GameSession session = load(id);
                Optional<Rejection> rejection = check(session, seat, decisionId, actionIndex);
                if (rejection.isPresent()) {
                    return rejection;
                }
                Decision decision = engine.decision(session.state().orElseThrow()).orElseThrow();
                if (apply(session, decision.actions().get(actionIndex))) {
                    break;
                }
            }
            playBots(id);
            return Optional.empty();
        });
    }

    /** A session left with a bot's decision (an instance stopped mid-turn) resumes the bot loop. */
    public void resumeBots(GameId id) {
        locks.run(id, () -> playBots(id));
    }

    private Optional<Rejection> check(GameSession session, PlayerId seat, String decisionId, int actionIndex) {
        if (session.status() == GameStatus.WAITING_FOR_OPPONENT) {
            return reject(Reason.GAME_NOT_STARTED, "The game starts when your opponent joins.");
        }
        Optional<Decision> pending = engine.decision(session.state().orElseThrow());
        if (pending.isEmpty()) {
            return reject(Reason.GAME_OVER, "The game is over.");
        }
        Decision decision = pending.get();
        if (!decision.id().equals(decisionId)) {
            return reject(Reason.STALE_DECISION, "Decision " + decisionId + " is no longer pending.");
        }
        if (decision.player() != seat) {
            return reject(Reason.NOT_YOUR_DECISION, "Decision " + decisionId + " is your opponent's.");
        }
        if (actionIndex < 0 || actionIndex >= decision.actions().size()) {
            return reject(Reason.INVALID_ACTION, "Decision " + decisionId + " has no action " + actionIndex + ".");
        }
        return Optional.empty();
    }

    /** While a bot holds the decision, it plays: one save and one update per move. */
    private void playBots(GameId id) {
        for (int moves = 0; ; moves++) {
            GameSession session = load(id);
            Optional<Seat.Bot> seat = botToPlay(session);
            if (seat.isEmpty()) {
                return;
            }
            if (moves == MAX_BOT_MOVES) {
                throw new IllegalStateException("Bots made " + MAX_BOT_MOVES + " moves in a row in game " + id);
            }
            pauseBetweenMoves(moves);
            GameState state = session.state().orElseThrow();
            Decision decision = engine.decision(state).orElseThrow();
            Bot bot = bots.rebuild(seat.get());
            Action action = bot.choose(engine.view(state, decision.player(), session.events()), decision);
            apply(session.withSeat(seat.get().withRngState(bot.rngState())), action);
        }
    }

    private Optional<Seat.Bot> botToPlay(GameSession session) {
        if (session.status() != GameStatus.IN_PROGRESS) {
            return Optional.empty();
        }
        return engine.decision(session.state().orElseThrow())
                .map(decision -> session.seat(decision.player()))
                .filter(Seat.Bot.class::isInstance)
                .map(Seat.Bot.class::cast);
    }

    private void pauseBetweenMoves(int moves) {
        if (moves > 0 && settings.botStepDelay().isPositive()) {
            try {
                Thread.sleep(settings.botStepDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted between two bot moves", e);
            }
        }
    }

    /** Applies the action and saves the session; false when another save came first. */
    private boolean apply(GameSession session, Action action) {
        GameState state = session.state().orElseThrow();
        Decision decision = engine.decision(state).orElseThrow();
        Transition transition = engine.apply(state, action);
        GameSession next = withUpdates(session.applied(action, transition, clock.instant()), transition.events());
        if (!save(session.version(), next)) {
            return false;
        }
        LOG.info("game={} seat={} decision={} action=\"{}\" version={} instance={}", session.id(),
                decision.player(), decision.kind(), labels.describe(action, state, decision.player()),
                next.version(), settings.instance());
        return true;
    }

    private boolean save(int expectedVersion, GameSession next) {
        try {
            repository.save(next, expectedVersion);
        } catch (VersionConflict conflict) {
            return false;
        }
        updates.publish(next.id(), next.version());
        return true;
    }

    /** Puts each seat's update for the session's latest version in its outbox. */
    private GameSession withUpdates(GameSession session, List<GameEvent> newEvents) {
        return session.withOutbox(session.outbox().with(session.version(), messages.updates(session, newEvents)));
    }

    private Transition setUp(Deck p1, Deck p2, long seed) {
        return engine.newGame(new GameSetup(p1, p2, seed));
    }

    private Deck legalDeck(String id) {
        Deck deck = content.decks().get(new DeckId(id));
        if (deck == null) {
            throw new SessionException.InvalidRequest("Unknown deck: " + id);
        }
        List<String> problems = new DeckValidator(content.catalog()).problems(deck);
        if (!problems.isEmpty()) {
            throw new SessionException.InvalidRequest("Deck " + id + " " + String.join("; ", problems));
        }
        return deck;
    }

    private GameSession load(GameId id) {
        return repository.find(id).orElseThrow(() -> new SessionException.UnknownGame(id.toString()));
    }

    private static DeckId deckOf(Seat seat) {
        return switch (seat) {
            case Seat.Human human -> human.deck();
            case Seat.Bot bot -> bot.deck();
            case Seat.Open open -> throw new IllegalStateException(open.player() + " has not joined yet");
        };
    }

    private static Optional<Rejection> reject(Reason reason, String message) {
        return Optional.of(new Rejection(reason, message));
    }
}
