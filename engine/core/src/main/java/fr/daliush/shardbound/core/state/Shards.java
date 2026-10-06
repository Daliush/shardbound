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

    /** End of turn: unspent Shards are lost (4.4). */
    public Shards emptied() {
        return new Shards(max, 0, lockedNextTurn);
    }

    public boolean canPay(int cost) {
        return cost <= available;
    }
}
