package fr.daliush.shardbound.api.domain.services.game;

import fr.daliush.shardbound.api.domain.bo.game.GameId;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Serializes the messages of one game inside this instance, so they do not conflict for nothing. Correctness
 * across instances comes from the versioned save, not from this lock (spec §13.4). Striped, so it stays bounded.
 */
@Component
public class GameLocks {

    private static final int STRIPES = 64;

    private final ReentrantLock[] stripes = new ReentrantLock[STRIPES];

    public GameLocks() {
        for (int i = 0; i < STRIPES; i++) {
            stripes[i] = new ReentrantLock(true);
        }
    }

    public <T> T call(GameId game, Supplier<T> work) {
        ReentrantLock lock = stripes[Math.floorMod(game.hashCode(), STRIPES)];
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    public void run(GameId game, Runnable work) {
        call(game, () -> {
            work.run();
            return null;
        });
    }
}
