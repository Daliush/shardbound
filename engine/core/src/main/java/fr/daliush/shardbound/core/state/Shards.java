package fr.daliush.shardbound.core.state;

/** A player's Shards (rulebook section 4): the maximum, what is left this turn, and what Overcharge locks. */
public record Shards(int max, int available, int lockedNextTurn) {

    public static final int CAP = 10;

    public static Shards none() {
        return new Shards(0, 0, 0);
    }

    /** Start of turn: one more max Shard, then refill minus the locked ones (4.1, 4.2, 11.4.4). */
    public Shards refilled() {
        int newMax = Math.min(CAP, max + 1);
        return new Shards(newMax, Math.max(0, newMax - lockedNextTurn), 0);
    }

    public Shards pay(int cost) {
        if (cost > available) {
            throw new IllegalStateException("Cannot pay " + cost + " with " + available + " Shards");
        }
        return new Shards(max, available - cost, lockedNextTurn);
    }

    /** 11.4.2, 11.4.3: more Shards locked for the next refill; they add up. */
    public Shards withLocked(int amount) {
        return new Shards(max, available, lockedNextTurn + amount);
    }

    /** 8.12: Shards for this turn only, which can go above the max. */
    public Shards gained(int amount) {
        return new Shards(max, available + amount, lockedNextTurn);
    }

    /** 8.12: one more max Shard, up to 10; the Shards of this turn do not change. */
    public Shards withMaxRaised() {
        return new Shards(Math.min(CAP, max + 1), available, lockedNextTurn);
    }

    /** End of turn: unspent Shards are lost (4.4). */
    public Shards emptied() {
        return new Shards(max, 0, lockedNextTurn);
    }

    public boolean canPay(int cost) {
        return cost <= available;
    }
}
