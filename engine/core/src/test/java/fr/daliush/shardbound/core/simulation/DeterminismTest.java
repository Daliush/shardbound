package fr.daliush.shardbound.core.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.bot.random.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.testing.GameDriver;
import fr.daliush.shardbound.core.testing.GameDriver.PlayedGame;
import fr.daliush.shardbound.core.testing.TestCards;
import fr.daliush.shardbound.core.testing.TestContent;
import org.junit.jupiter.api.Test;

/** Same seed, same decks and same actions give exactly the same game (spec §7). */
class DeterminismTest {

    private final Content content = TestContent.content();
    private final GameEngine engine = new GameEngine(content.catalog());

    @Test
    void theSameSeedsReplayTheSameGame() {
        for (int seed = 1; seed <= 20; seed++) {
            PlayedGame first = play(seed);
            PlayedGame second = play(seed);

            assertThat(second.events()).isEqualTo(first.events());
            assertThat(second.finalState()).isEqualTo(first.finalState());
        }
    }

    @Test
    void randomDiscardsAndRandomTargetsReplayToo() {
        GameEngine withTestCards = new GameEngine(TestCards.CATALOG);
        for (int seed = 1; seed <= 20; seed++) {
            GameSetup setup = new GameSetup(TestCards.EFFECTS_DECK, content.deck("root-starter"), seed);
            PlayedGame first = GameDriver.play(withTestCards, setup, new RandomBot(seed), new RandomBot(-seed));
            PlayedGame second = GameDriver.play(withTestCards, setup, new RandomBot(seed), new RandomBot(-seed));

            assertThat(second.events()).isEqualTo(first.events());
            assertThat(second.finalState()).isEqualTo(first.finalState());
        }
    }

    @Test
    void anotherSeedGivesAnotherGame() {
        assertThat(play(1).events()).isNotEqualTo(play(2).events());
    }

    private PlayedGame play(int seed) {
        GameSetup setup = new GameSetup(content.deck("ember-starter"), content.deck("root-starter"), seed);
        return GameDriver.play(engine, setup, new RandomBot(seed * 7L), new RandomBot(seed * 13L));
    }
}
