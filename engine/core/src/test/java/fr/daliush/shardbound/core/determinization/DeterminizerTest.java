package fr.daliush.shardbound.core.determinization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.Player;
import fr.daliush.shardbound.core.bot.random.RandomBot;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckValidator;
import fr.daliush.shardbound.core.content.Faction;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.testing.GameDriver;
import fr.daliush.shardbound.core.testing.HiddenCards;
import fr.daliush.shardbound.core.testing.Invariants;
import fr.daliush.shardbound.core.testing.TestCards;
import fr.daliush.shardbound.core.testing.TestContent;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The Determinizer works from a view alone: it keeps what the view shows and guesses the rest (spec §10). */
class DeterminizerTest {

    private static final List<String> STARTER_DECKS = List.of("ember-starter", "root-starter", "tide-starter");
    /** The cards that put a card back in its owner's hand in public: Fracture, return to hand, recall. */
    private static final Set<CardId> RETURNING_CARDS = Set.of(new CardId("tide.moonpull"),
            new CardId("tide.spring-tide"), new CardId("tide.receding-wave"), new CardId("ember.rise-from-cinders"));
    private static final CardId BEHEMOTH = new CardId("tide.deepcurrent-behemoth");
    private static final int ENGULF = 1;

    private final Content content = TestContent.content();
    private final CardCatalog catalog = content.catalog();
    private final GameEngine engine = new GameEngine(catalog);
    private final Determinizer determinizer = new Determinizer(catalog);

    @Test
    void aDeterminizedGameShowsItsPlayerExactlyTheViewItCameFrom() {
        AtomicInteger checked = new AtomicInteger();
        for (int seed = 1; seed <= 60; seed++) {
            int gameSeed = seed;
            playChecking(engine, starterDecks(seed), new RandomBot(seed), new RandomBot(-seed), (view, real) -> {
                GameState world = determinizer.determinize(view, gameSeed);
                PlayerId opponent = view.viewer().opponent();

                assertThat(engine.view(world, view.viewer(), view.history())).isEqualTo(view);
                Invariants.check(world, engine.decision(world), catalog);
                assertThat(new DeckValidator(catalog).problems(world.player(opponent).decklist()))
                        .as("the opponent's guessed cards make a legal deck (section 2)").isEmpty();
                assertThat(world.firstPlayer()).isEqualTo(real.firstPlayer());
                assertThat(world.nextInstanceId()).isEqualTo(real.nextInstanceId());
                assertThat(world.decisionSeq()).isEqualTo(real.decisionSeq());
                for (PlayerId id : PlayerId.values()) {
                    assertThat(world.player(id).turnsTaken()).isEqualTo(real.player(id).turnsTaken());
                    assertThat(world.player(id).mulliganDecided()).isEqualTo(real.player(id).mulliganDecided());
                }
                checked.incrementAndGet();
            });
        }
        assertThat(checked).hasPositiveValue();
    }

    @Test
    void everyActionOfTheDecisionCanBeAppliedToTheDeterminizedGame() {
        GameEngine withTestCards = new GameEngine(TestCards.CATALOG);
        Determinizer withTestCardsDeterminizer = new Determinizer(TestCards.CATALOG);
        for (int seed = 1; seed <= 30; seed++) {
            int gameSeed = seed;
            GameSetup setup = seed % 2 == 0 ? starterDecks(seed)
                    : new GameSetup(TestCards.EFFECTS_DECK, content.deck(STARTER_DECKS.get(seed % 3)), seed);
            playChecking(withTestCards, setup, new RandomBot(seed), new RandomBot(-seed), (view, real) -> {
                GameState world = withTestCardsDeterminizer.determinize(view, gameSeed);
                Invariants.check(world, withTestCards.decision(world), TestCards.CATALOG);
                for (Action action : view.decision().orElseThrow().actions()) {
                    assertThatCode(() -> withTestCards.apply(world, action)).as("%s", action)
                            .doesNotThrowAnyException();
                }
            });
        }
    }

    /** The proof that it never copies the real hidden cards: change them all, and it guesses exactly the same. */
    @Test
    void itGivesTheSameGameWhateverTheCardsItsPlayerMayNotSee() {
        for (int seed = 1; seed <= 30; seed++) {
            int gameSeed = seed;
            playChecking(engine, starterDecks(seed), new RandomBot(seed), new RandomBot(-seed), (view, real) -> {
                GameState other = HiddenCards.rearranged(real, view.viewer());
                PlayerView otherView = engine.view(other, view.viewer(), view.history());

                assertThat(determinizer.determinize(otherView, gameSeed))
                        .isEqualTo(determinizer.determinize(view, gameSeed));
            });
        }
    }

    @Test
    void cardsThatWentBackToTheOpponentsHandInPublicAreKeptExactly() {
        AtomicInteger knownCards = new AtomicInteger();
        for (int seed = 1; seed <= 40; seed++) {
            int gameSeed = seed;
            GameSetup setup = seed % 2 == 0
                    ? new GameSetup(content.deck("tide-starter"), content.deck("ember-starter"), seed)
                    : new GameSetup(content.deck("ember-starter"), content.deck("tide-starter"), seed);
            playChecking(engine, setup, returningCards(seed), returningCards(-seed), (view, real) -> {
                PlayerId opponent = view.viewer().opponent();
                List<HandCard> guessedHand = determinizer.determinize(view, gameSeed).player(opponent).hand();
                Set<InstanceId> wentBack = view.history().stream().flatMap(event -> switch (event) {
                    case GameEvent.FractureAdvanced advanced -> Stream.of(advanced.card().id());
                    case GameEvent.ReturnedToHand returned -> Stream.of(returned.card().id());
                    case GameEvent.Recalled recalled -> Stream.of(recalled.card().id());
                    default -> Stream.<InstanceId>empty();
                }).collect(Collectors.toSet());
                for (HandCard card : real.player(opponent).hand()) {
                    if (wentBack.contains(card.id())) {
                        assertThat(guessedHand).contains(card);
                        knownCards.incrementAndGet();
                    }
                }
            });
        }
        assertThat(knownCards).as("known cards met in the opponent's hand").hasPositiveValue();
    }

    @Test
    void theOpponentsUnknownCardsAreLegalCardsOfTheirFactionOrNeutral() {
        for (int seed = 1; seed <= 20; seed++) {
            int gameSeed = seed;
            playChecking(engine, starterDecks(seed), new RandomBot(seed), new RandomBot(-seed), (view, real) -> {
                PlayerId opponent = view.viewer().opponent();
                PlayerState guessed = determinizer.determinize(view, gameSeed).player(opponent);
                Faction faction = view.opponent().faction();
                List<CardInstance> unknown = new ArrayList<>(guessed.deck());
                guessed.hand().stream().map(HandCard::card).forEach(unknown::add);

                assertThat(unknown).allSatisfy(card -> {
                    CardDefinition definition = catalog.card(card.card());
                    assertThat(definition.isToken()).isFalse();
                    assertThat(definition.faction()).isIn(faction, Faction.NEUTRAL);
                    assertThat(card.owner()).isEqualTo(opponent);
                });
            });
        }
    }

    /** Decided 2026-10-08: drawn uniformly among the remaining copies, so 2 copies left are twice as likely as 1. */
    @Test
    void aCardWithTwoCopiesLeftIsDrawnTwiceAsOftenAsACardWithOne() {
        PlayerView view = aViewWithOneOpponentCardSeen();
        PlayerId opponent = view.viewer().opponent();
        Map<CardId, Long> seenCopies = view.opponent().graveyard().stream()
                .collect(Collectors.groupingBy(CardInstance::card, Collectors.counting()));
        CardId oneLeft = seenCopies.keySet().iterator().next();
        CardId twoLeft = catalog.all().stream()
                .filter(card -> !card.isToken() && card.faction() == view.opponent().faction())
                .map(CardDefinition::id)
                .filter(id -> !seenCopies.containsKey(id))
                .findFirst()
                .orElseThrow();

        long draws = 6000;
        long withOneLeft = 0;
        long withTwoLeft = 0;
        for (long seed = 0; seed < draws; seed++) {
            List<HandCard> hand = determinizer.determinize(view, seed).player(opponent).hand();
            withOneLeft += hand.stream().filter(card -> card.card().card().equals(oneLeft)).count();
            withTwoLeft += hand.stream().filter(card -> card.card().card().equals(twoLeft)).count();
        }
        double ratio = (double) withTwoLeft / withOneLeft;
        assertThat(ratio).as("%s copies of %s for %s of %s", withTwoLeft, twoLeft, withOneLeft, oneLeft)
                .isBetween(1.7, 2.3);
    }

    @Test
    void onlyAViewWhosePlayerDecidesCanBeDeterminized() {
        GameState start = engine.newGame(starterDecks(1)).state();
        PlayerId waiting = engine.decision(start).orElseThrow().player().opponent();
        PlayerView view = engine.view(start, waiting, List.of());

        assertThatThrownBy(() -> determinizer.determinize(view, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theHistoryMustStartWithTheGame() {
        GameState start = engine.newGame(starterDecks(1)).state();
        PlayerId decider = engine.decision(start).orElseThrow().player();
        PlayerView view = engine.view(start, decider, List.of());

        assertThatThrownBy(() -> determinizer.determinize(view, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A view of P1 at their first main decision after P2 has a card in their graveyard. */
    private PlayerView aViewWithOneOpponentCardSeen() {
        AtomicReference<PlayerView> found = new AtomicReference<>();
        for (int seed = 1; found.get() == null; seed++) {
            playChecking(engine, starterDecks(seed), new RandomBot(seed), new RandomBot(-seed), (view, real) -> {
                if (found.get() == null && view.viewer() == PlayerId.P1
                        && view.decision().orElseThrow().kind() == DecisionKind.MAIN
                        && view.opponent().graveyard().size() == 1
                        && view.opponent().units().stream().allMatch(unit -> unit.token())
                        && view.opponent().relics().isEmpty() && KnownHand.of(view).isEmpty()) {
                    found.set(view);
                }
            });
        }
        return found.get();
    }

    /** Every pair of starter decks, each deck on both seats, in turn. */
    private GameSetup starterDecks(int seed) {
        List<Deck> decks = STARTER_DECKS.stream().map(content::deck).toList();
        Deck first = decks.get(seed % decks.size());
        Deck second = decks.get((seed + 1) % decks.size());
        return seed % (2 * decks.size()) < decks.size() ? new GameSetup(first, second, seed)
                : new GameSetup(second, first, seed);
    }

    /** Plays a game, and checks each view a player decides on against the real game it comes from. */
    private static void playChecking(GameEngine engine, GameSetup setup, Player p1, Player p2,
                                     BiConsumer<PlayerView, GameState> check) {
        AtomicReference<GameState> real = new AtomicReference<>();
        GameDriver.play(engine, setup, checking(p1, real, check), checking(p2, real, check),
                (state, decision) -> real.set(state));
    }

    private static Player checking(Player player, AtomicReference<GameState> real,
                                   BiConsumer<PlayerView, GameState> check) {
        return (view, decision) -> {
            check.accept(view, real.get());
            return player.choose(view, decision);
        };
    }

    /** Plays the cards that put cards back in hand whenever it can, and engulfs with the Behemoth; else at random. */
    private static Player returningCards(long seed) {
        RandomBot random = new RandomBot(seed);
        return (view, decision) -> decision.actions().stream()
                .filter(action -> returnsACard(view, action))
                .findFirst()
                .orElseGet(() -> random.choose(view, decision));
    }

    private static boolean returnsACard(PlayerView view, Action action) {
        return switch (action) {
            case Action.PlayCard play -> view.self().hand().stream()
                    .anyMatch(card -> card.id().equals(play.card()) && RETURNING_CARDS.contains(card.card().card()));
            case Action.Attack attack -> attack.attackIndex() == ENGULF && view.self().units().stream()
                    .anyMatch(unit -> unit.id().equals(attack.attacker()) && unit.card().equals(BEHEMOTH));
            default -> false;
        };
    }
}
