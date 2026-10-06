package fr.daliush.shardbound.api.dao.game;

import java.util.UUID;

/** Another save of the same game came first: reload and start again. */
public final class VersionConflict extends RuntimeException {

    public VersionConflict(UUID id, int expected, int found) {
        super("Game " + id + " is at version " + found + ", not " + expected);
    }
}
