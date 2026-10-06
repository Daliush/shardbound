package fr.daliush.shardbound.api.session;

/** One seat's live connection on this instance, whatever the transport. */
public interface SeatConnection {

    /** Unique across instances. */
    String id();

    void send(String message);

    /** A newer connection took the seat (spec §13.1). */
    void replaced();
}
