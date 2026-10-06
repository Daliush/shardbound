package fr.daliush.shardbound.core.random;

import java.util.List;

/** A small, fast, seedable random generator whose whole state is one {@code long}, so a game can store it. */
public final class SplitMix64 {

    private long state;

    public SplitMix64(long state) {
        this.state = state;
    }

    public long state() {
        return state;
    }

    public long nextLong() {
        state += 0x9E3779B97F4A7C15L;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A uniform integer in {@code [0, bound)}. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    public boolean nextBoolean() {
        return nextLong() < 0;
    }

    /** Fisher–Yates shuffle, in place. */
    public <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T swapped = list.get(i);
            list.set(i, list.get(j));
            list.set(j, swapped);
        }
    }
}
