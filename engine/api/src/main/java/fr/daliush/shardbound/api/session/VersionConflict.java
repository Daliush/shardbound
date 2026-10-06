package fr.daliush.shardbound.api.session;

/** Another save of the same game came first: reload and start again. */
public final class VersionConflict extends RuntimeException {

    public VersionConflict(GameId id, int expected, int found) {
        super("Game " + id + " is at version " + found + ", not " + expected);
    }
}
