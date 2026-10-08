package fr.daliush.shardbound.core.determinization;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.DeckValidator;
import fr.daliush.shardbound.core.content.Faction;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What the opponent's unknown cards are drawn from: 2 copies of each card of their faction and of each neutral card,
 * no tokens (2.2, 2.3, 2.4), minus every copy seen. The viewer knows the opponent's faction, never their decklist.
 */
final class RemainingCopies {

    private final PlayerId owner;
    private final List<CardId> copies;

    private RemainingCopies(PlayerId owner, List<CardId> copies) {
        this.owner = owner;
        this.copies = copies;
    }

    static RemainingCopies of(CardCatalog catalog, PlayerView view, SeenCards seen) {
        PlayerId opponent = view.viewer().opponent();
        Faction faction = view.opponent().faction();
        Map<CardId, Long> seenCopies = seen.ownedBy(opponent).stream()
                .collect(Collectors.groupingBy(CardInstance::card, Collectors.counting()));
        List<CardId> copies = new ArrayList<>();
        for (CardDefinition card : catalog.all()) {
            if (!card.isToken() && (card.faction() == faction || card.faction() == Faction.NEUTRAL)) {
                long left = DeckValidator.MAX_COPIES - seenCopies.getOrDefault(card.id(), 0L);
                for (long copy = 0; copy < left; copy++) {
                    copies.add(card.id());
                }
            }
        }
        return new RemainingCopies(opponent, copies);
    }

    /** One card per id, drawn uniformly among the remaining copies: a card with 2 copies left is twice as likely. */
    List<CardInstance> draw(List<InstanceId> ids, SplitMix64 rng) {
        if (ids.size() > copies.size()) {
            throw new IllegalStateException(owner + " has " + ids.size() + " unknown cards, but only " + copies.size()
                    + " copies are left for them");
        }
        List<CardId> shuffled = new ArrayList<>(copies);
        rng.shuffle(shuffled);
        List<CardInstance> drawn = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            drawn.add(new CardInstance(ids.get(i), shuffled.get(i), owner));
        }
        return drawn;
    }
}
