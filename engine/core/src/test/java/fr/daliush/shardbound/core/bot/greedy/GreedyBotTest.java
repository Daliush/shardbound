package fr.daliush.shardbound.core.bot.greedy;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.Player;
import fr.daliush.shardbound.core.bot.random.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.rules.play.Costs;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.testing.BotMatches;
import fr.daliush.shardbound.core.testing.GameDriver;
import fr.daliush.shardbound.core.testing.GameDriver.PlayedGame;
import fr.daliush.shardbound.core.testing.HiddenCards;
import fr.daliush.shardbound.core.testing.Invariants;
import fr.daliush.shardbound.core.testing.TestCards;
import fr.daliush.shardbound.core.testing.TestContent;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GreedyBotTest {

    private final Content content = TestContent.content();
    private final GameEngine engine = new GameEngine(content.catalog());

    /** The driver applies every pick, and the engine refuses an action that is not listed. */
    @Test
    void itPlaysWholeGamesWithLegalActionsOnly() {
        GameEngine withTestCards = new GameEngine(TestCards.CATALOG);
        for (int seed = 1; seed <= 12; seed++) {
            Deck greedyDeck = seed % 2 == 0 ? TestCards.EFFECTS_DECK : starterDeck(seed);
            GameSetup setup = new GameSetup(greedyDeck, starterDeck(seed + 1), seed);
            Player opponent = seed % 3 == 0 ? new GreedyBot(withTestCards, -seed) : new RandomBot(-seed);
            PlayedGame game = GameDriver.play(withTestCards, setup, new GreedyBot(withTestCards, seed), opponent,
                    (state, decision) -> Invariants.check(state, decision, TestCards.CATALOG));

            assertThat(game.finalState().result()).as("game %s has a result", seed).isPresent();
        }
    }

    @Test
    void theSameSeedsPlayTheSameGame() {
        for (int seed = 1; seed <= 5; seed++) {
            GameSetup setup = new GameSetup(starterDeck(seed), starterDeck(seed + 1), seed);
            PlayedGame first = GameDriver.play(engine, setup, new GreedyBot(engine, seed), new GreedyBot(engine, -seed));
            PlayedGame second = GameDriver.play(engine, setup, new GreedyBot(engine, seed), new GreedyBot(engine, -seed));

            assertThat(second.events()).isEqualTo(first.events());
            assertThat(second.finalState()).isEqualTo(first.finalState());
        }
    }

    /** What the game server does (spec §13.4): only the generator's state is kept between two moves. */
    @Test
    void aBotRebuiltFromItsGeneratorStateAtEveryMovePlaysTheSameGame() {
        for (int seed = 1; seed <= 5; seed++) {
            GameSetup setup = new GameSetup(starterDeck(seed), starterDeck(seed + 2), seed);
            PlayedGame kept = GameDriver.play(engine, setup, new GreedyBot(engine, seed), new RandomBot(-seed));
            PlayedGame rebuilt = GameDriver.play(engine, setup, rebuiltAtEveryMove(seed), new RandomBot(-seed));

            assertThat(rebuilt.events()).isEqualTo(kept.events());
        }
    }

    @Test
    void itPicksTheSameWhateverTheCardsItMayNotSee() {
        for (int seed = 1; seed <= 5; seed++) {
            GameSetup setup = new GameSetup(starterDeck(seed), starterDeck(seed + 1), seed);
            AtomicReference<GameState> real = new AtomicReference<>();
            GreedyBot bot = new GreedyBot(engine, seed);
            Player checking = (view, decision) -> {
                PlayerView other = engine.view(HiddenCards.rearranged(real.get(), view.viewer()), view.viewer(),
                        view.history());
                Action onTheOtherGame = new GreedyBot(engine, bot.rngState()).choose(other, decision);
                Action picked = bot.choose(view, decision);
                assertThat(onTheOtherGame).isEqualTo(picked);
                return picked;
            };
            GameDriver.play(engine, setup, checking, new RandomBot(-seed), (state, decision) -> real.set(state));
        }
    }

    @Test
    void itTakesAMulliganWhenNoCardOfItsHandCostsTwoOrLess() {
        Set<Action> answers = new HashSet<>();
        for (int seed = 1; seed <= 40; seed++) {
            Transition setup = engine.newGame(new GameSetup(starterDeck(seed), starterDeck(seed + 1), seed));
            GameState start = setup.state();
            Decision mulligan = engine.decision(start).orElseThrow();
            PlayerId decider = mulligan.player();
            PlayerState hand = start.player(decider);
            boolean hasACheapCard = hand.hand().stream().anyMatch(card ->
                    Costs.toPlay(content.catalog(), hand, start.player(decider.opponent()), card, false) <= 2);

            Action answer = new GreedyBot(engine, seed).choose(engine.view(start, decider, setup.events()), mulligan);

            assertThat(mulligan.kind()).isEqualTo(DecisionKind.MULLIGAN);
            assertThat(answer).isEqualTo(hasACheapCard ? new Action.KeepHand() : new Action.Mulligan());
            answers.add(answer);
        }
        assertThat(answers).as("both answers met").hasSize(2);
    }

    private Player rebuiltAtEveryMove(long seed) {
        long[] rngState = {seed};
        return (view, decision) -> {
            GreedyBot bot = new GreedyBot(engine, rngState[0]);
            Action action = bot.choose(view, decision);
            rngState[0] = bot.rngState();
            return action;
        };
    }

    private Deck starterDeck(int index) {
        return content.deck(BotMatches.STARTER_DECKS.get(index % BotMatches.STARTER_DECKS.size()));
    }
}
