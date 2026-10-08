package fr.daliush.shardbound.core.simulation;

import fr.daliush.shardbound.core.bot.Player;
import fr.daliush.shardbound.core.bot.greedy.GreedyBot;
import fr.daliush.shardbound.core.bot.random.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.testing.BotMatches;
import fr.daliush.shardbound.core.testing.BotMatches.Game;
import fr.daliush.shardbound.core.testing.TestContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongFunction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Measures that take minutes, run on demand: {@code ./mvnw -pl core test -Preports}. They print Markdown tables and
 * write them to {@code core/target/reports/}; their numbers go in the pull request (spec §10).
 */
@Tag("report")
class BotReportsTest {

    private static final int GREEDY_AGAINST_RANDOM_GAMES = 1800;
    private static final int BALANCE_GAMES = 1200;

    private final Content content = TestContent.content();
    private final GameEngine engine = new GameEngine(content.catalog());

    @Test
    void greedyAgainstRandom() throws IOException {
        List<Game> games = BotMatches.play(engine, content, seed -> new GreedyBot(engine, seed), RandomBot::new,
                BotMatches.everyDeckAgainstEveryDeck(), GREEDY_AGAINST_RANDOM_GAMES);

        write("greedy-against-random.md", "## Greedy (A) against random\n"
                + BotMatches.table("By greedy's deck", games, "Greedy's deck", game -> game.pairing().deckOfA())
                + BotMatches.table("By random's deck", games, "Random's deck", game -> game.pairing().deckOfB())
                + BotMatches.table("By greedy's seat", games, "Greedy's seat", game -> game.seatOfA().name())
                + BotMatches.table("By pairing", games, "Greedy's deck vs random's",
                        game -> game.pairing().deckOfA() + " vs " + game.pairing().deckOfB())
                + "\n" + BotMatches.durations(games));
    }

    /** Decided 2026-10-08: the balance of the decks is measured with the best bot available, today GreedyBot. */
    @Test
    void deckBalance() throws IOException {
        write("deck-balance.md", balance("greedy", seed -> new GreedyBot(engine, seed)));
    }

    /** The decks' balance as {@code bot} plays them against itself: each pair of decks, each deck on each seat. */
    private String balance(String botName, LongFunction<Player> bot) {
        List<Game> games = BotMatches.play(engine, content, bot, bot, BotMatches.eachPairOfDecks(), BALANCE_GAMES);
        return "## Deck balance, " + botName + " against " + botName + "\n"
                + BotMatches.tableByDeck("Each deck over all its games", games)
                + BotMatches.tableByGroup("Each pair of decks: the first deck's win rate (A)", games, "Pairing",
                        game -> game.pairing().deckOfA() + " vs " + game.pairing().deckOfB())
                + "\nThe first player wins " + BotMatches.firstPlayerWinRate(games) + ".\n\n"
                + BotMatches.durations(games);
    }

    private static void write(String file, String report) throws IOException {
        System.out.println(report);
        Path reports = Path.of("target", "reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve(file), report);
    }
}
