package fr.daliush.shardbound.core.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.testing.GameDriver;
import fr.daliush.shardbound.core.testing.GameDriver.PlayedGame;
import fr.daliush.shardbound.core.testing.Invariants;
import fr.daliush.shardbound.core.testing.TestCards;
import fr.daliush.shardbound.core.testing.TestContent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Full games between random bots over the starter decks: every game ends, and the invariants hold after every step.
 */
class RandomGamesTest {

    private static final int GAMES = 1000;
    private static final List<String> STARTER_DECKS = List.of("ember-starter", "root-starter", "tide-starter");

    private final Content content = TestContent.content();
    private final GameEngine engine = new GameEngine(content.catalog());

    @Test
    void thousandRandomGamesEndAndKeepTheInvariants() {
        long start = System.nanoTime();
        for (int seed = 1; seed <= GAMES; seed++) {
            PlayedGame game = GameDriver.play(engine, setup(seed), new RandomBot(seed * 31L), new RandomBot(seed * 17L),
                    (state, decision) -> Invariants.check(state, decision, content.catalog()));
            assertThat(game.finalState().result()).as("game %s has a result", seed).isPresent();
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("%d random games in %d ms%n", GAMES, millis);
        assertThat(millis).as("1,000 random games run within a minute").isLessThan(60_000);
    }

    @Test
    void everyListedActionCanBeApplied() {
        for (int seed = 1; seed <= 20; seed++) {
            GameDriver.play(engine, setup(seed), new RandomBot(seed), new RandomBot(-seed), (state, decision) ->
                    decision.ifPresent(pending -> {
                        for (Action action : pending.actions()) {
                            assertThatCode(() -> engine.apply(state, action)).as("%s", action)
                                    .doesNotThrowAnyException();
                        }
                    }));
        }
    }

    @Test
    void randomGamesWithTheEffectsOfTestCardsEndAndKeepTheInvariants() {
        GameEngine withTestCards = new GameEngine(TestCards.CATALOG);
        for (int seed = 1; seed <= 300; seed++) {
            Deck opponent = content.deck(STARTER_DECKS.get(seed % STARTER_DECKS.size()));
            GameSetup setup = new GameSetup(TestCards.EFFECTS_DECK, opponent, seed);
            boolean checkEveryAction = seed <= 20;
            PlayedGame game = GameDriver.play(withTestCards, setup, new RandomBot(seed * 31L), new RandomBot(seed * 17L),
                    (state, decision) -> {
                        Invariants.check(state, decision, TestCards.CATALOG);
                        if (checkEveryAction) {
                            decision.ifPresent(pending -> pending.actions().forEach(action ->
                                    assertThatCode(() -> withTestCards.apply(state, action)).as("%s", action)
                                            .doesNotThrowAnyException()));
                        }
                    });
            assertThat(game.finalState().result()).as("game %s has a result", seed).isPresent();
        }
    }

    /** Every pair of starter decks, each deck on both seats, in turn. */
    private GameSetup setup(int seed) {
        List<Deck> decks = STARTER_DECKS.stream().map(content::deck).toList();
        Deck first = decks.get(seed % decks.size());
        Deck second = decks.get((seed + 1) % decks.size());
        return seed % (2 * decks.size()) < decks.size() ? new GameSetup(first, second, seed)
                : new GameSetup(second, first, seed);
    }
}
