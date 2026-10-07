package fr.daliush.shardbound.core.rules.aura;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardType;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;

/** 8.14: what the cost auras on the board add to a card of one type for one player. 6.8 sums it with the rest. */
public final class CostAuras {

    private CostAuras() {
    }

    public static int change(CardCatalog catalog, PlayerState player, PlayerState opponent, CardType type) {
        return ActiveAura.on(catalog, player, opponent).stream()
                .filter(active -> active.aura() instanceof Effect.CostAura cost
                        && appliesTo(cost, active.controller(), player.id()) && cost.cardType().matches(type))
                .mapToInt(active -> ((Effect.CostAura) active.aura()).change())
                .sum();
    }

    private static boolean appliesTo(Effect.CostAura cost, PlayerId auraController, PlayerId player) {
        return switch (cost.player()) {
            case YOU -> auraController == player;
            case OPPONENT -> auraController != player;
        };
    }
}
