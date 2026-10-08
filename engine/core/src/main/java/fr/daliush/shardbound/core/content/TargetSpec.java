package fr.daliush.shardbound.core.content;

/** Closed list of targets (rulebook section 10), plus the attack's target inside attack abilities. */
public enum TargetSpec {
    ALLY_UNIT, ENEMY_UNIT, ANY_UNIT,
    RANDOM_ALLY_UNIT, RANDOM_ENEMY_UNIT,
    SELF, ATTACK_TARGET,
    ALL_ALLY_UNITS, ALL_ENEMY_UNITS, ALL_UNITS,
    YOU, OPPONENT, ANY_PLAYER,
    ALLY_RELIC, ENEMY_RELIC, ANY_RELIC;

    /** Whether the effect's controller picks this target (10.2). */
    public boolean isChosen() {
        return switch (this) {
            case ALLY_UNIT, ENEMY_UNIT, ANY_UNIT, ANY_PLAYER, ALLY_RELIC, ENEMY_RELIC, ANY_RELIC -> true;
            default -> false;
        };
    }

    public boolean isGroup() {
        return this == ALL_ALLY_UNITS || this == ALL_ENEMY_UNITS || this == ALL_UNITS;
    }

    public boolean isRelic() {
        return this == ALLY_RELIC || this == ENEMY_RELIC || this == ANY_RELIC;
    }
}
