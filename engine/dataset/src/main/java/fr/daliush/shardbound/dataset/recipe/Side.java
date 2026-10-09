package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Locale;

/** Whose unit it is, seen from the player who asks the question. */
public enum Side {
    MINE, OPPONENTS;

    public PlayerId of(PlayerId asker) {
        return this == MINE ? asker : asker.opponent();
    }

    /** "mine", "opponents": a part of a scenario's id. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The sides whose units this target can hit, from its controller's point of view (section 10). */
    public static List<Side> hitBy(TargetSpec target) {
        return switch (target) {
            case ALLY_UNIT, RANDOM_ALLY_UNIT, ALL_ALLY_UNITS, SELF -> List.of(MINE);
            case ENEMY_UNIT, RANDOM_ENEMY_UNIT, ALL_ENEMY_UNITS, ATTACK_TARGET -> List.of(OPPONENTS);
            case ANY_UNIT, ALL_UNITS -> List.of(MINE, OPPONENTS);
            case YOU, OPPONENT, ANY_PLAYER, ALLY_RELIC, ENEMY_RELIC, ANY_RELIC -> List.of();
        };
    }
}
