package fr.daliush.shardbound.core.content;

/** The cards a cost aura applies to (8.14). */
public enum CardTypeFilter {
    UNIT, SPELL, RELIC, ANY;

    public boolean matches(CardType type) {
        return this == ANY || name().equals(type.name());
    }
}
