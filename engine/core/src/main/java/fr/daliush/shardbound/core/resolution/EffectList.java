package fr.daliush.shardbound.core.resolution;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import java.util.List;

/** Where a list of effects is printed on a card. The state stores this address, not the effects themselves. */
public sealed interface EffectList {

    CardId card();

    List<Effect> effects(CardCatalog catalog);

    /** {@code fractureStep}: the Fracture step played, 0-based; 0 for any other spell. */
    record SpellEffects(CardId card, int fractureStep) implements EffectList {

        @Override
        public List<Effect> effects(CardCatalog catalog) {
            return ((SpellCard) catalog.card(card)).effectsAtStep(fractureStep);
        }
    }

    record AttackEffects(CardId card, int attackIndex) implements EffectList {

        @Override
        public List<Effect> effects(CardCatalog catalog) {
            return catalog.unit(card).attacks().get(attackIndex).effects();
        }
    }

    record AbilityEffects(CardId card, int abilityIndex) implements EffectList {

        @Override
        public List<Effect> effects(CardCatalog catalog) {
            return switch (catalog.card(card)) {
                case UnitCard unit -> unit.abilities().get(abilityIndex).effects();
                case RelicCard relic -> relic.abilities().get(abilityIndex).effects();
                case SpellCard spell -> throw new IllegalStateException(spell.id() + " has no abilities");
            };
        }
    }
}
