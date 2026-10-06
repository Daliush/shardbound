package fr.daliush.shardbound.api.session;

/** A seat's {@code state} message, and the version it shows. */
public record SeatState(int version, String message) {
}
