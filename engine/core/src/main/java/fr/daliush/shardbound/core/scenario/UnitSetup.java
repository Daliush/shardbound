package fr.daliush.shardbound.core.scenario;

import java.util.OptionalInt;

/** How a scenario unit differs from a fresh one that arrived on an earlier turn. */
public final class UnitSetup {

    OptionalInt defense = OptionalInt.empty();
    boolean arrivedThisTurn;
    boolean hasAttacked;
    boolean hasIntercepted;
    int frozenThroughTurn;
    boolean anchorProtected;
    boolean doomed;

    /** The current defense, at most its max; 0 is only legal for a doomed unit ({@link #doomed}). */
    public UnitSetup defense(int value) {
        defense = OptionalInt.of(value);
        return this;
    }

    /** It arrived this turn, so it cannot attack yet (7.2). */
    public UnitSetup arrivedThisTurn() {
        arrivedThisTurn = true;
        return this;
    }

    /** It has already attacked this turn (7.2). */
    public UnitSetup hasAttacked() {
        hasAttacked = true;
        return this;
    }

    /** It has already intercepted this turn (7.5). */
    public UnitSetup hasIntercepted() {
        hasIntercepted = true;
        return this;
    }

    /** Frozen until the end of this turn (8.10). */
    public UnitSetup frozenThroughTurn(int turn) {
        frozenThroughTurn = turn;
        return this;
    }

    /** Still protected by Anchor (11.3.1), whether or not the card has the keyword. */
    public UnitSetup anchorProtected() {
        anchorProtected = true;
        return this;
    }

    /** At 0 defense and doomed (11.3.4). */
    public UnitSetup doomed() {
        doomed = true;
        defense = OptionalInt.of(0);
        return this;
    }
}
