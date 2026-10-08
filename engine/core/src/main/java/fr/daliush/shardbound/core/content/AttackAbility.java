package fr.daliush.shardbound.core.content;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** One of a unit's attack abilities (7.1), possibly with Echo X (11.1). */
public record AttackAbility(Optional<String> name, int cost, List<Effect> effects, OptionalInt echo) {

    public AttackAbility {
        effects = List.copyOf(effects);
    }

    /** An attack without an effect on its target cannot be intercepted (7.8). */
    public boolean hasTarget() {
        return effects.stream().anyMatch(effect -> effect.targets(TargetSpec.ATTACK_TARGET));
    }

    /**
     * 11.1.2: what its echo replays, the printed effects with every amount at X%, rounded toward 0 (11.1.10);
     * effects without an amount apply in full, and a Sacrifice keeps its count (11.1.11).
     */
    public List<Effect> echoEffects() {
        int percent = echo.orElseThrow(() -> new IllegalStateException("This attack ability has no Echo"));
        return effects.stream().map(effect -> scaled(effect, percent)).toList();
    }

    private static Effect scaled(Effect effect, int percent) {
        return switch (effect) {
            case Effect.Damage damage -> new Effect.Damage(scaled(damage.amount(), percent), damage.target());
            case Effect.Heal heal -> new Effect.Heal(scaled(heal.amount(), percent), heal.target());
            case Effect.Modify modify -> new Effect.Modify(scaled(modify.attackDamage(), percent),
                    scaled(modify.defense(), percent), modify.duration(), modify.target());
            case Effect.Draw draw -> new Effect.Draw(scaled(draw.amount(), percent), draw.target());
            case Effect.Discard discard -> new Effect.Discard(scaled(discard.amount(), percent), discard.target(),
                    discard.choice());
            case Effect.Summon summon -> new Effect.Summon(summon.token(), scaled(summon.count(), percent));
            case Effect.GainShards gain when gain.mode() == GainMode.THIS_TURN ->
                    new Effect.GainShards(gain.mode(), scaled(gain.amount(), percent));
            default -> effect;
        };
    }

    /** Integer division rounds toward 0, for maluses too: Echo 50 on -3 gives -1. */
    private static int scaled(int value, int percent) {
        return value * percent / 100;
    }
}
