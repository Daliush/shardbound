package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.List;

/** 8.7 Discard X: cards go from a player's hand to the graveyard, chosen by that player or at random. */
final class DiscardEffect {

    private DiscardEffect() {
    }

    /** The player who discards. */
    static List<PlayerId> discarders(Game game, Effect.Discard discard, EffectSource source, List<TargetRef> chosen) {
        return Targets.resolve(game, discard.target(), source, chosen).stream()
                .map(target -> ((TargetRef.PlayerTarget) target).player())
                .toList();
    }

    /** Every way to pick the cards, in hand order; the whole hand when it holds no more than asked. */
    static List<List<InstanceId>> options(PlayerState player, int amount) {
        List<InstanceId> hand = player.hand().stream().map(HandCard::id).toList();
        return Combinations.of(hand, Math.min(amount, hand.size()));
    }

    static void atRandom(Game game, Effect.Discard discard, EffectSource source, List<TargetRef> chosen) {
        for (PlayerId player : discarders(game, discard, source, chosen)) {
            for (int i = 0; i < discard.amount() && !game.player(player).hand().isEmpty(); i++) {
                List<HandCard> hand = game.player(player).hand();
                discard(game, player, hand.get(game.rng().nextInt(hand.size())));
            }
        }
    }

    static void apply(Game game, List<InstanceId> cards) {
        for (InstanceId id : cards) {
            for (PlayerId player : PlayerId.values()) {
                game.player(player).handCard(id).ifPresent(card -> discard(game, player, card));
            }
        }
    }

    /** The graveyard is public, so the card is revealed. A Fracture card loses its progress (11.2.6). */
    private static void discard(Game game, PlayerId player, HandCard card) {
        game.updatePlayer(player, state -> state.removeFromHand(card.id()).addToGraveyard(card.card()));
        game.emit(new GameEvent.CardDiscarded(player, card.card(), GameEvent.DiscardReason.EFFECT,
                card.fractureStep() > 0 ? List.of("8.7", "11.2.6") : List.of("8.7")));
    }
}
