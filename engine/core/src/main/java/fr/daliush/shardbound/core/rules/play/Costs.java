package fr.daliush.shardbound.core.rules.play;

import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.state.HandCard;

/** 6.8: the printed cost (or the next Fracture step's), minus 2 when overcharged, floored at 0 once. */
public final class Costs {

    public static final int OVERCHARGE_DISCOUNT = 2;

    private Costs() {
    }

    public static int toPlay(CardDefinition card, HandCard inHand, boolean overcharged) {
        int total = printedCost(card, inHand) - (overcharged ? OVERCHARGE_DISCOUNT : 0);
        return Math.max(0, total);
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
