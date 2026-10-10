package fr.daliush.shardbound.dataset.recipe.anchor;

import fr.daliush.shardbound.core.scenario.UnitSetup;
import fr.daliush.shardbound.dataset.recipe.Side;
import java.util.List;
import java.util.Locale;

/**
 * Whether an Anchor unit is still protected (11.3.1): how to set it up, how a question says it, and which rules explain
 * it.
 */
enum Protection {
    PROTECTED(List.of("11.3.1")),
    EXPIRED(List.of("11.3.1", "11.3.6"));

    private final List<String> contextRules;

    Protection(List<String> contextRules) {
        this.contextRules = contextRules;
    }

    /** Why the unit is, or is no longer, protected: no event says it. */
    List<String> contextRules() {
        return contextRules;
    }

    String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 11.3.1: protected from its arrival until its controller's next turn starts. */
    UnitSetup setUp(UnitSetup unit, Side side) {
        return switch (this) {
            case PROTECTED -> side == Side.MINE ? unit.arrivedThisTurn().anchorProtected() : unit.anchorProtected();
            case EXPIRED -> unit;
        };
    }

    /** "My opponent played Root Sentinel on their last turn", as the asker sees it. */
    String situation(String unit, Side side) {
        return switch (side) {
            case MINE -> this == PROTECTED
                    ? "I played " + unit + " this turn"
                    : "My " + unit + " has been on the board since my last turn";
            case OPPONENTS -> this == PROTECTED
                    ? "My opponent played " + unit + " on their last turn"
                    : "My opponent's " + unit + " has been on the board since before their last turn";
        };
    }
}
