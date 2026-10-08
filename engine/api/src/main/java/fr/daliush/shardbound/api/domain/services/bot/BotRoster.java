package fr.daliush.shardbound.api.domain.services.bot;

import fr.daliush.shardbound.api.domain.bo.game.Seat;
import fr.daliush.shardbound.core.bot.Bot;
import fr.daliush.shardbound.core.bot.random.RandomBot;
import fr.daliush.shardbound.core.random.SplitMix64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import org.springframework.stereotype.Component;

/** The bots a game can be played against, by name, and how to rebuild one from its seat. */
@Component
public class BotRoster {

    /** Keeps the bots' generators apart from the game's, which starts from the same seed. */
    private static final long BOT_STREAM = 0x5EA7B07L;

    private final Map<String, LongFunction<Bot>> bots = new LinkedHashMap<>();

    public BotRoster() {
        bots.put("random", RandomBot::new);
    }

    public List<String> names() {
        return List.copyOf(bots.keySet());
    }

    public boolean has(String name) {
        return bots.containsKey(name);
    }

    /** Derived from the game seed, so a game against a bot replays from its setup and the human's actions. */
    public long firstState(long gameSeed) {
        return new SplitMix64(gameSeed ^ BOT_STREAM).nextLong();
    }

    public Bot rebuild(Seat.Bot seat) {
        LongFunction<Bot> bot = bots.get(seat.name());
        if (bot == null) {
            throw new IllegalStateException("Unknown bot " + seat.name());
        }
        return bot.apply(seat.rngState());
    }
}
