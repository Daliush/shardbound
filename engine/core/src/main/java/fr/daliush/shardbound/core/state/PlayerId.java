package fr.daliush.shardbound.core.state;

/** A seat. Who plays first is decided by the RNG (5.1.1). */
public enum PlayerId {
    P1, P2;

    public PlayerId opponent() {
        return this == P1 ? P2 : P1;
    }
}
