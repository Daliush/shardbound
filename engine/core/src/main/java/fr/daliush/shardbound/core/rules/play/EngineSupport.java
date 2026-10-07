package fr.daliush.shardbound.core.rules.play;

import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import java.util.Set;

/**
 * The cards the engine can play so far. The engine is built in slices; until every effect and keyword exists,
 * a card that needs a missing one is never offered as a legal action.
 */
public final class EngineSupport {

    private static final Set<Class<? extends Effect>> IMPLEMENTED_EFFECTS = Set.of(
            Effect.Damage.class, Effect.Destroy.class, Effect.Sacrifice.class, Effect.Heal.class, Effect.Modify.class,
            Effect.Draw.class, Effect.Discard.class, Effect.ReturnToHand.class, Effect.Summon.class,
            Effect.Freeze.class, Effect.GainShards.class);

    private EngineSupport() {
    }

    public static boolean supports(CardDefinition card) {
        return card.keywords().isEmpty()
                && !(card instanceof SpellCard spell && spell.isFracture())
                && !(card instanceof UnitCard unit && unit.attacks().stream().anyMatch(a -> a.echo().isPresent()))
                && Triggers.abilitiesOf(card).stream().noneMatch(ability -> ability.trigger() == Trigger.CONTINUOUS)
                && card.allEffects().allMatch(effect -> IMPLEMENTED_EFFECTS.contains(effect.getClass()));
    }
}
