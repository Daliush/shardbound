package fr.daliush.shardbound.core.determinization;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckEntry;
import fr.daliush.shardbound.core.content.DeckId;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.view.OpponentState;
import fr.daliush.shardbound.core.view.PlayerView;
import fr.daliush.shardbound.core.view.SelfState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Each player's side of the determinized game: what the view shows, as it is, completed with the guessed cards. */
final class Sides {

    private static final DeckId GUESSED_DECK = new DeckId("determinized");

    private Sides() {
    }

    static PlayerState viewer(PlayerView view, List<CardInstance> deck, DerivedFields derived) {
        SelfState self = view.self();
        return new PlayerState(view.viewer(), self.faction(), view.ownDeck(), self.hp(), self.fatigue(), self.shards(),
                derived.turnsTaken(view.viewer()), self.mulliganDecided(), deck, self.hand(), self.units(),
                self.relics(), self.graveyard());
    }

    /**
     * Their known cards first, then as many unknown cards as their hand holds; the other unknown cards are their deck.
     * Their decklist is every card they have in this game, so the game stays complete.
     */
    static PlayerState opponent(PlayerView view, SeenCards seen, List<HandCard> knownHand,
                                List<CardInstance> unknownCards, DerivedFields derived) {
        PlayerId id = view.viewer().opponent();
        OpponentState opponent = view.opponent();
        int unknownInHand = opponent.handCount() - knownHand.size();
        if (unknownInHand < 0) {
            throw new IllegalStateException(id + " holds " + opponent.handCount() + " cards, but "
                    + knownHand.size() + " are known in their hand");
        }
        List<HandCard> hand = new ArrayList<>(knownHand);
        unknownCards.subList(0, unknownInHand).stream().map(HandCard::fresh).forEach(hand::add);
        List<CardInstance> deck = unknownCards.subList(unknownInHand, unknownCards.size());
        Deck decklist = decklist(opponent, Stream.concat(seen.ownedBy(id).stream(), unknownCards.stream()).toList());
        return new PlayerState(id, opponent.faction(), decklist, opponent.hp(), opponent.fatigue(), opponent.shards(),
                derived.turnsTaken(id), derived.opponentMulliganDecided(), deck, hand, opponent.units(),
                opponent.relics(), opponent.graveyard());
    }

    private static Deck decklist(OpponentState opponent, List<CardInstance> cards) {
        Map<CardId, Integer> copies = new LinkedHashMap<>();
        cards.forEach(card -> copies.merge(card.card(), 1, Integer::sum));
        List<DeckEntry> entries = copies.entrySet().stream()
                .map(entry -> new DeckEntry(entry.getKey(), entry.getValue()))
                .toList();
        return new Deck(GUESSED_DECK, "Determinized", Optional.empty(), opponent.faction(), entries);
    }
}
