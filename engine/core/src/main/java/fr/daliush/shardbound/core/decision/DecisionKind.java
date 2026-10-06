package fr.daliush.shardbound.core.decision;

public enum DecisionKind {
    MULLIGAN, MAIN, INTERCEPT, CHOOSE_TARGET, CHOOSE_CARDS, CHOOSE_ORDER;

    /**
     * Whether this decision is asked in the middle of a step, which waits for the answer.
     * Answering MULLIGAN or MAIN starts new work instead.
     */
    public boolean pausesAStep() {
        return this != MULLIGAN && this != MAIN;
    }
}
