package fr.daliush.shardbound.core.content;

/** Closed list of effects (rulebook section 8). */
public sealed interface Effect {

    /** An effect that applies to a target. */
    sealed interface Targeted extends Effect {
        TargetSpec target();
    }

    record Damage(int amount, TargetSpec target) implements Targeted {

        /** The damage dealt with a bonus, never below 0 (8.15). */
        public int withBonus(int bonus) {
            return Math.max(0, amount + bonus);
        }
    }

    record Destroy(TargetSpec target) implements Targeted {}

    record Sacrifice(int count) implements Effect {}

    record Heal(int amount, TargetSpec target) implements Targeted {}

    record Modify(int attackDamage, int defense, Duration duration, TargetSpec target) implements Targeted {}

    record Draw(int amount, TargetSpec target) implements Targeted {}

    record Discard(int amount, TargetSpec target, DiscardChoice choice) implements Targeted {}

    record ReturnToHand(TargetSpec target) implements Targeted {}

    record Summon(CardId token, int count) implements Effect {}

    record Freeze(TargetSpec target) implements Targeted {}

    record Link() implements Effect {}

    /** {@code amount} is 1 in {@link GainMode#MAX} mode. */
    record GainShards(GainMode mode, int amount) implements Effect {}

    record Recall() implements Effect {}

    record StatAura(TargetSpec target, int attackDamage, int defense) implements Targeted {}

    record CostAura(PlayerSide player, CardTypeFilter cardType, int change) implements Effect {}

    default boolean targets(TargetSpec spec) {
        return this instanceof Targeted targeted && targeted.target() == spec;
    }
}
