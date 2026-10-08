package fr.daliush.shardbound.core.bot.greedy;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.rules.play.Costs;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerState;

/** The mulligan rule (spec §10): a hand with no card costing 2 or less goes back for a new one (5.1.3). */
final class OpeningHand {

    static final int CHEAP_CARD_COST = 2;

    private OpeningHand() {
    }

    static Action answer(GameState world, Decision mulligan, CardCatalog catalog) {
        PlayerState bot = world.player(mulligan.player());
        PlayerState opponent = world.player(mulligan.player().opponent());
        boolean hasACheapCard = bot.hand().stream()
                .anyMatch(card -> Costs.toPlay(catalog, bot, opponent, card, false) <= CHEAP_CARD_COST);
        Action wanted = hasACheapCard ? new Action.KeepHand() : new Action.Mulligan();
        return mulligan.actions().stream().filter(wanted::equals).findFirst().orElseThrow();
    }
}
