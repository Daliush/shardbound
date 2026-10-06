package fr.daliush.shardbound.core.scenario;

import java.util.OptionalInt;

/** How a scenario unit differs from a fresh one that arrived on an earlier turn. */
public final class UnitSetup {

    OptionalInt defense = OptionalInt.empty();
    boolean arrivedThisTurn;
    boolean attacked;
    boolean intercepted;
    int frozenThroughTurn;

    public UnitSetup defense(int value) {
        defense = OptionalInt.of(value);
        return this;
    }

    public UnitSetup arrivedThisTurn() {
        arrivedThisTurn = true;
        return this;
    }

    public UnitSetup attacked() {
        attacked = true;
        return this;
    }

    public UnitSetup intercepted() {
        intercepted = true;
        return this;
    }

    public UnitSetup frozenThroughTurn(int turn) {
        frozenThroughTurn = turn;
        return this;
    }
}
