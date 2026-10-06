package fr.daliush.shardbound.core.rules.turn;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.List;
import java.util.Optional;

/** Drawing one card: fatigue from an empty deck (1.4), discard into a full hand (3.3). */
public final class CardDraws {

    private CardDraws() {
    }

    /** {@code rule} is the rule that makes the player draw, for the trace. */
    public static void draw(Game game, PlayerId player, String rule) {
        PlayerState state = game.player(player);
        if (state.deck().isEmpty()) {
            int fatigue = state.fatigue() + 1;
            game.updatePlayer(player, p -> p.withFatigue(fatigue).withHp(p.hp() - fatigue));
            game.emit(new GameEvent.HpLost(player, fatigue, GameEvent.HpLossReason.FATIGUE, List.of(rule, "1.4")));
            return;
        }
        CardInstance top = state.deck().getFirst();
        game.updatePlayer(player, p -> p.withDeck(p.deck().subList(1, p.deck().size())));
        if (state.handIsFull()) {
            game.updatePlayer(player, p -> p.addToGraveyard(top));
            game.emit(new GameEvent.CardDiscarded(player, top, GameEvent.DiscardReason.OVERDRAW,
                    List.of(rule, "3.3")));
        } else {
            game.updatePlayer(player, p -> p.addToHand(HandCard.fresh(top)));
            game.emit(new GameEvent.CardDrawn(player, Optional.of(top), List.of(rule)));
        }
    }
}
