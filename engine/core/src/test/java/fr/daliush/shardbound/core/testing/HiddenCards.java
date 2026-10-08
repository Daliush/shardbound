package fr.daliush.shardbound.core.testing;

import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.ArrayList;
import java.util.List;

/** Games that differ from another only in what one player may not see (3.2, 3.3). */
public final class HiddenCards {

    private HiddenCards() {
    }

    /**
     * The same game, except for what {@code viewer} may not see: the opponent's hand and deck hold their cards in
     * another arrangement, the viewer's deck is in another order, and the game's generator is another one.
     */
    public static GameState rearranged(GameState state, PlayerId viewer) {
        PlayerState self = state.player(viewer);
        PlayerState opponent = state.player(viewer.opponent());
        List<CardInstance> hidden = new ArrayList<>(opponent.hand().stream().map(HandCard::card).toList());
        hidden.addAll(opponent.deck());
        List<CardInstance> reversed = hidden.reversed();
        List<HandCard> hand = new ArrayList<>();
        for (int i = 0; i < opponent.hand().size(); i++) {
            HandCard before = opponent.hand().get(i);
            hand.add(new HandCard(reversed.get(i), before.fractureStep(), before.lastFractureTurn()));
        }
        PlayerState newSelf = self.withDeck(self.deck().reversed());
        PlayerState newOpponent = opponent.withHand(hand)
                .withDeck(reversed.subList(opponent.hand().size(), reversed.size()));
        PlayerState p1 = viewer == PlayerId.P1 ? newSelf : newOpponent;
        PlayerState p2 = viewer == PlayerId.P1 ? newOpponent : newSelf;
        return new GameState(state.turn(), state.active(), state.firstPlayer(), p1, p2, state.resolution(),
                state.pending(), new SplitMix64(state.rng()).nextLong(), state.nextInstanceId(),
                state.nextArrivalSeq(), state.decisionSeq(), state.result());
    }
}
