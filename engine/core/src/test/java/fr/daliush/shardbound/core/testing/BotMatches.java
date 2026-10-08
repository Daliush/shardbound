package fr.daliush.shardbound.core.testing;

import fr.daliush.shardbound.core.bot.Player;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

/**
 * Seeded games between two bots, A and B, over pairings of decks: the pairings in turn, and each bot on each seat
 * half the time. Same arguments, same games.
 */
public final class BotMatches {

    public static final List<String> STARTER_DECKS = List.of("ember-starter", "root-starter", "tide-starter");

    private BotMatches() {
    }

    /** Which deck each bot plays. */
    public record Pairing(String deckOfA, String deckOfB) {}

    /** One game: the decks, where A sat, who played first, how it ended and how long it took. */
    public record Game(Pairing pairing, PlayerId seatOfA, PlayerId firstPlayer, GameResult result, long millis) {

        public boolean wonByA() {
            return result instanceof GameResult.Win win && win.winner() == seatOfA;
        }

        public boolean isDraw() {
            return result instanceof GameResult.Draw;
        }

        /** The deck that won, empty for a draw. */
        public Optional<String> winningDeck() {
            if (!(result instanceof GameResult.Win win)) {
                return Optional.empty();
            }
            return Optional.of(win.winner() == seatOfA ? pairing.deckOfA() : pairing.deckOfB());
        }
    }

    /** Every deck against every deck, mirrors included: each bot plays every deck. */
    public static List<Pairing> everyDeckAgainstEveryDeck() {
        return STARTER_DECKS.stream()
                .flatMap(deckOfA -> STARTER_DECKS.stream().map(deckOfB -> new Pairing(deckOfA, deckOfB)))
                .toList();
    }

    /** Each pair of different decks once. */
    public static List<Pairing> eachPairOfDecks() {
        List<Pairing> pairings = new ArrayList<>();
        for (int i = 0; i < STARTER_DECKS.size(); i++) {
            for (int j = i + 1; j < STARTER_DECKS.size(); j++) {
                pairings.add(new Pairing(STARTER_DECKS.get(i), STARTER_DECKS.get(j)));
            }
        }
        return pairings;
    }

    /** Game {@code n} plays pairing {@code n % size}, with A on P1 in one round of pairings and on P2 in the next. */
    public static List<Game> play(GameEngine engine, Content content, LongFunction<Player> botA,
                                  LongFunction<Player> botB, List<Pairing> pairings, int games) {
        List<Game> played = new ArrayList<>();
        for (int n = 0; n < games; n++) {
            long seed = n + 1L;
            Pairing pairing = pairings.get(n % pairings.size());
            PlayerId seatOfA = (n / pairings.size()) % 2 == 0 ? PlayerId.P1 : PlayerId.P2;
            Player a = botA.apply(seed * 31);
            Player b = botB.apply(seed * 17);
            GameSetup setup = seatOfA == PlayerId.P1
                    ? new GameSetup(content.deck(pairing.deckOfA()), content.deck(pairing.deckOfB()), seed)
                    : new GameSetup(content.deck(pairing.deckOfB()), content.deck(pairing.deckOfA()), seed);
            long start = System.nanoTime();
            GameDriver.PlayedGame game = seatOfA == PlayerId.P1 ? GameDriver.play(engine, setup, a, b)
                    : GameDriver.play(engine, setup, b, a);
            long millis = (System.nanoTime() - start) / 1_000_000;
            played.add(new Game(pairing, seatOfA, game.finalState().firstPlayer(),
                    game.finalState().result().orElseThrow(), millis));
        }
        return played;
    }

    public static WinRate winRateOfA(List<Game> games) {
        return new WinRate(games.stream().filter(Game::wonByA).count(), games.stream().filter(Game::isDraw).count(),
                games.size());
    }

    /** A Markdown table of A's win rate by each value of {@code key}, and overall. */
    public static String table(String title, List<Game> games, String keyName, Function<Game, String> key) {
        return tableByGroup(title, games, keyName, key) + "| **all** | " + winRateOfA(games) + " |\n";
    }

    /** The same table without the total, for games where A is not the same deck from one group to the next. */
    public static String tableByGroup(String title, List<Game> games, String keyName, Function<Game, String> key) {
        StringBuilder table = new StringBuilder(header(title, keyName, "A's win rate [95% CI]"));
        games.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()))
                .forEach((value, group) -> table.append("| ").append(value).append(" | ")
                        .append(winRateOfA(group)).append(" |\n"));
        return table.toString();
    }

    /** Each deck's win rate over every game it played, as A or as B: the balance of the decks. */
    public static String tableByDeck(String title, List<Game> games) {
        StringBuilder table = new StringBuilder(header(title, "Deck", "Win rate [95% CI]"));
        for (String deck : STARTER_DECKS) {
            List<Game> played = games.stream().filter(game -> game.pairing().deckOfA().equals(deck)
                    || game.pairing().deckOfB().equals(deck)).toList();
            long wins = played.stream().filter(game -> game.winningDeck().equals(Optional.of(deck))).count();
            long draws = played.stream().filter(Game::isDraw).count();
            table.append("| ").append(deck).append(" | ").append(new WinRate(wins, draws, played.size()))
                    .append(" |\n");
        }
        return table.toString();
    }

    private static String header(String title, String keyName, String valueName) {
        return "\n### " + title + "\n\n| " + keyName + " | " + valueName + " |\n|---|---|\n";
    }

    /** How long the games took, for the time budget (spec §10). */
    public static String durations(List<Game> games) {
        long total = games.stream().mapToLong(Game::millis).sum();
        long longest = games.stream().mapToLong(Game::millis).max().orElse(0);
        return String.format(Locale.ROOT, "%d games in %.1f s: %.0f ms per game on average, %d ms at most%n",
                games.size(), total / 1000.0, (double) total / Math.max(1, games.size()), longest);
    }

    /** How often the player who played first won, whichever bot it was (5.1.1). */
    public static WinRate firstPlayerWinRate(List<Game> games) {
        long wins = games.stream()
                .filter(game -> game.result() instanceof GameResult.Win win && win.winner() == game.firstPlayer())
                .count();
        return new WinRate(wins, games.stream().filter(Game::isDraw).count(), games.size());
    }
}
