package fr.daliush.shardbound.core.state;

/** What the stat auras of one card currently give a unit (8.14). */
public record AuraBonus(CardInstance source, int attackDamage, int defense) {
}
