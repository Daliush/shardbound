package fr.daliush.shardbound.api.domain.services.game;

import fr.daliush.shardbound.api.domain.bo.command.NewGame;
import fr.daliush.shardbound.api.domain.bo.command.SeatAccess;
import fr.daliush.shardbound.api.domain.bo.error.GameException;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.Seat;
import fr.daliush.shardbound.api.domain.ports.GameSessionPort;
import fr.daliush.shardbound.api.domain.services.bot.BotRoster;
import fr.daliush.shardbound.api.domain.services.security.SeatTokens;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckId;
import fr.daliush.shardbound.core.content.DeckValidator;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.state.PlayerId;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Creating a game takes seat P1; against a bot it starts at once, against a human it waits for the join, which
 * takes seat P2 and sets the game up as version 1 (spec §12, §13.2).
 */
@Service
public class GameCreationService {

    private final Content content;
    private final GameEngine engine;
    private final GameSessionPort sessions;
    private final GameSaver saver;
    private final GameLocks locks;
    private final BotTurnService botTurns;
    private final BotRoster roster;
    private final SeatTokens tokens;
    private final Clock clock;
    private final SecureRandom seeds = new SecureRandom();

    public GameCreationService(Content content, GameEngine engine, GameSessionPort sessions, GameSaver saver,
                               GameLocks locks, BotTurnService botTurns, BotRoster roster, SeatTokens tokens,
                               Clock clock) {
        this.content = content;
        this.engine = engine;
        this.sessions = sessions;
        this.saver = saver;
        this.locks = locks;
        this.botTurns = botTurns;
        this.roster = roster;
        this.tokens = tokens;
        this.clock = clock;
    }

    public SeatAccess create(NewGame request) {
        Deck deck = legalDeck(request.deck());
        long seed = request.seed().orElseGet(seeds::nextLong);
        GameId id = GameId.random();
        String token = tokens.generate();
        Seat.Human creator = new Seat.Human(PlayerId.P1, deck.id(), tokens.hash(token));
        return switch (request.opponent()) {
            case NewGame.Opponent.Bot bot -> {
                if (!roster.has(bot.name())) {
                    throw new GameException.InvalidRequest("Unknown bot: " + bot.name());
                }
                Deck botDeck = legalDeck(bot.deck());
                Seat.Bot seat = new Seat.Bot(PlayerId.P2, botDeck.id(), bot.name(), roster.firstState(seed));
                sessions.create(GameSession.started(id, seed, creator, seat, setUp(deck, botDeck, seed),
                        clock.instant()));
                botTurns.resume(id);
                yield new SeatAccess(id, token, Optional.empty());
            }
            case NewGame.Opponent.Human ignored -> {
                String joinCode = tokens.generate();
                sessions.create(GameSession.waiting(id, seed, creator,
                        new Seat.Open(PlayerId.P2, tokens.hash(joinCode)), clock.instant()));
                yield new SeatAccess(id, token, Optional.of(joinCode));
            }
        };
    }

    public SeatAccess join(GameId id, String joinCode, String deckId) {
        Deck deck = legalDeck(deckId);
        return locks.call(id, () -> {
            while (true) {
                GameSession session = sessions.load(id);
                if (!(session.p2() instanceof Seat.Open open) || !tokens.matches(joinCode, open.joinCodeHash())) {
                    throw new GameException.JoinRefused();
                }
                String token = tokens.generate();
                Seat.Human joiner = new Seat.Human(PlayerId.P2, deck.id(), tokens.hash(token));
                Transition setup = setUp(creatorDeck(session), deck, session.seed());
                if (saver.save(session.joined(joiner, setup, clock.instant()), session.version(), setup.events())) {
                    return new SeatAccess(id, token, Optional.empty());
                }
            }
        });
    }

    private Transition setUp(Deck p1, Deck p2, long seed) {
        return engine.newGame(new GameSetup(p1, p2, seed));
    }

    private Deck creatorDeck(GameSession session) {
        if (!(session.p1() instanceof Seat.Human creator)) {
            throw new IllegalStateException("Seat P1 of game " + session.id() + " is not its creator's");
        }
        return content.deck(creator.deck().value());
    }

    private Deck legalDeck(String id) {
        Deck deck = content.decks().get(new DeckId(id));
        if (deck == null) {
            throw new GameException.InvalidRequest("Unknown deck: " + id);
        }
        List<String> problems = new DeckValidator(content.catalog()).problems(deck);
        if (!problems.isEmpty()) {
            throw new GameException.InvalidRequest("Deck " + id + " " + String.join("; ", problems));
        }
        return deck;
    }
}
