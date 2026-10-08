package fr.daliush.shardbound.core.determinization;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.ArrayList;
import java.util.List;

/** The viewer's draw pile: their decklist minus their cards seen elsewhere, in an order they never see (3.2). */
final class OwnDeck {

    private OwnDeck() {
    }

    static List<CardInstance> shuffled(PlayerView view, SeenCards seen, InstanceIds ids, SplitMix64 rng) {
        PlayerId viewer = view.viewer();
        List<CardId> left = new ArrayList<>(view.ownDeck().cardList());
        for (CardInstance card : seen.ownedBy(viewer)) {
            if (!left.remove(card.card())) {
                throw new IllegalStateException(card + " is not in the decklist of " + viewer);
            }
        }
        List<InstanceId> unseen = ids.unseenOf(viewer);
        if (left.size() != view.self().deckCount() || unseen.size() != left.size()) {
            throw new IllegalStateException("The deck of " + viewer + " has " + view.self().deckCount()
                    + " cards, but " + left.size() + " are left in the decklist with " + unseen.size() + " unseen ids");
        }
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            deck.add(new CardInstance(unseen.get(i), left.get(i), viewer));
        }
        rng.shuffle(deck);
        return deck;
    }
}
