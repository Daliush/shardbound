package fr.daliush.shardbound.core.rules.play;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.rules.aura.CostAuras;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerState;

/** What playing a card (6.8) or using an attack ability costs, in one place for legality, payment and labels. */
public final class Costs {

    public static final int OVERCHARGE_DISCOUNT = 2;

    private Costs() {
    }

    /**
     * 6.8: what {@code player} pays for a card in their hand: the printed cost (or the next Fracture step's), plus
     * the cost auras on both boards, minus 2 when overcharged, floored at 0 once.
     */
    public static int toPlay(CardCatalog catalog, PlayerState player, PlayerState opponent, HandCard inHand,
                             boolean overcharged) {
        CardDefinition card = catalog.card(inHand.card().card());
        int total = printedCost(card, inHand) + CostAuras.change(catalog, player, opponent, card.type())
                - (overcharged ? OVERCHARGE_DISCOUNT : 0);
        return Math.max(0, total);
    }

    /** The printed cost: Overcharge never applies to attacks (11.4.5) and cost auras only change cards (8.14). */
    public static int toAttack(AttackAbility attack) {
        return attack.cost();
    }

    private static int printedCost(CardDefinition card, HandCard inHand) {
        return switch (card) {
            case UnitCard unit -> unit.cost().orElseThrow(() -> new IllegalStateException(unit.id() + " is a token"));
            case RelicCard relic -> relic.cost();
            case SpellCard spell -> spell.isFracture()
                    ? spell.fracture().get(inHand.fractureStep()).cost()
                    : spell.cost().orElseThrow();
        };
    }
}
