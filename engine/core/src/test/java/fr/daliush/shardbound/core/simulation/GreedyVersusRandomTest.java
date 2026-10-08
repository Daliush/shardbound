package fr.daliush.shardbound.core.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.bot.greedy.GreedyBot;
import fr.daliush.shardbound.core.bot.random.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.testing.BotMatches;
import fr.daliush.shardbound.core.testing.BotMatches.Game;
import fr.daliush.shardbound.core.testing.TestContent;
import fr.daliush.shardbound.core.testing.WinRate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The target of spec §10: GreedyBot wins at least 60% of its games against RandomBot. The decks rotate, each bot plays
 * every deck and each seat half the time; the verdict is the lower bound of the 95% interval, so it stays clear. A
 * report with many more games runs on demand ({@code BotReportsTest}).
 */
class GreedyVersusRandomTest {

    /** 10 rounds of the 9 pairings of decks on both seats. */
    private static final int GAMES = 180;
    private static final double TARGET = 0.60;
    private static final long TIME_BUDGET_MILLIS = 120_000;

    private final Content content = TestContent.content();
    private final GameEngine engine = new GameEngine(content.catalog());

    @Test
    void greedyWinsAtLeastSixtyPercentOfItsGamesAgainstRandom() {
        List<Game> games = BotMatches.play(engine, content, seed -> new GreedyBot(engine, seed), RandomBot::new,
                BotMatches.everyDeckAgainstEveryDeck(), GAMES);

        WinRate greedy = BotMatches.winRateOfA(games);
        System.out.print(BotMatches.table("Greedy (A) against random", games, "Greedy's deck",
                game -> game.pairing().deckOfA()));
        System.out.print(BotMatches.durations(games));
        assertThat(greedy.low()).as("greedy's win rate: %s", greedy).isGreaterThanOrEqualTo(TARGET);
        assertThat(games.stream().mapToLong(Game::millis).sum()).as("the games of the build, in ms")
                .isLessThan(TIME_BUDGET_MILLIS);
    }
}
