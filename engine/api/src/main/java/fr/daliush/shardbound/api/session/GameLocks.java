package fr.daliush.shardbound.api.session;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes the messages of one game inside this instance, so they do not conflict for nothing. Correctness
 * across instances comes from the versioned save, not from this lock (spec §13.4). Striped, so it stays bounded.
 */
final class GameLocks {

    private static final int STRIPES = 64;

    private final ReentrantLock[] stripes = new ReentrantLock[STRIPES];

    GameLocks() {
        for (int i = 0; i < STRIPES; i++) {
            stripes[i] = new ReentrantLock(true);
        }
    }

    <T> T call(GameId game, Supplier<T> work) {
        ReentrantLock lock = stripes[Math.floorMod(game.hashCode(), STRIPES)];
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    void run(GameId game, Runnable work) {
        call(game, () -> {
            work.run();
            return null;
        });
    }
}
