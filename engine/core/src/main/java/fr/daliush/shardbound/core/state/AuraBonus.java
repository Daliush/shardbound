package fr.daliush.shardbound.core.state;

/** What one stat aura currently gives a unit (8.14). */
public record AuraBonus(InstanceId source, int attackDamage, int defense) {
}
