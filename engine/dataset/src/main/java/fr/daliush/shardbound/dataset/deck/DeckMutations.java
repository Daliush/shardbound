package fr.daliush.shardbound.dataset.deck;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckEntry;
import fr.daliush.shardbound.core.content.DeckValidator;
import fr.daliush.shardbound.core.content.Faction;
import fr.daliush.shardbound.core.random.SplitMix64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The decks the questions ask about: each starter deck as it is, then changed so that it breaks one deck-building rule
 * or none. The cards each change moves are drawn with a fixed seed, so a dataset version always asks about the same
 * decks.
 */
final class DeckMutations {

    private static final int SWAPS = 2;
    private static final int ADDED = 2;
    private static final int REMOVED = 2;
    private static final int THIRD_COPIES = 4;
    private static final int PER_OTHER_FACTION = 2;
    private static final int TOKENS = 4;

    private final CardCatalog catalog;
    private final SplitMix64 random;

    DeckMutations(CardCatalog catalog, long seed) {
        this.catalog = catalog;
        this.random = new SplitMix64(seed);
    }

    List<MutatedDeck> of(Deck starter) {
        List<CardId> inDeck = starter.cards().stream().map(DeckEntry::card).toList();
        List<CardId> twoCopies = starter.cards().stream().filter(entry -> entry.count() == DeckValidator.MAX_COPIES)
                .map(DeckEntry::card).toList();
        List<CardId> addable = cards(card -> !card.isToken() && fits(card, starter)
                && copies(starter, card.id()) < DeckValidator.MAX_COPIES);
        List<MutatedDeck> decks = new ArrayList<>();
        decks.add(MutatedDeck.starter(starter));
        pairs(addable, inDeck, SWAPS).forEach(pair -> decks.add(
                MutatedDeck.changed(starter, Mutation.LEGAL, List.of(pair.added()), List.of(pair.removed()))));
        picks(addable, ADDED).forEach(card -> decks.add(
                MutatedDeck.changed(starter, Mutation.CARD_COUNT, List.of(card), List.of())));
        picks(inDeck, REMOVED).forEach(card -> decks.add(
                MutatedDeck.changed(starter, Mutation.CARD_COUNT, List.of(), List.of(card))));
        pairs(twoCopies, inDeck, THIRD_COPIES).forEach(pair -> decks.add(
                MutatedDeck.changed(starter, Mutation.COPIES, List.of(pair.added()), List.of(pair.removed()))));
        for (Faction other : otherFactions(starter.faction())) {
            List<CardId> theirs = cards(card -> !card.isToken() && card.faction() == other);
            pairs(theirs, inDeck, PER_OTHER_FACTION).forEach(pair -> decks.add(
                    MutatedDeck.changed(starter, Mutation.FACTION, List.of(pair.added()), List.of(pair.removed()))));
        }
        // A token of another faction would break 2.2 as well as 2.4.
        List<CardId> tokens = cards(card -> card.isToken() && fits(card, starter));
        pairs(tokens, inDeck, TOKENS).forEach(pair -> decks.add(
                MutatedDeck.changed(starter, Mutation.TOKEN, List.of(pair.added()), List.of(pair.removed()))));
        return decks;
    }

    /** {@code count} distinct cards. */
    private List<CardId> picks(List<CardId> cards, int count) {
        List<CardId> shuffled = new ArrayList<>(cards);
        random.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    /**
     * {@code count} changes, each adding one card of {@code added} and removing a different card of {@code removed}.
     * The added cards cycle when there are fewer of them than changes, such as the one token; the removed ones never
     * repeat, so every change gives another deck.
     */
    private List<Swap> pairs(List<CardId> added, List<CardId> removed, int count) {
        List<CardId> adds = new ArrayList<>(added);
        List<CardId> removes = new ArrayList<>(removed);
        random.shuffle(adds);
        random.shuffle(removes);
        List<Swap> swaps = new ArrayList<>();
        for (int index = 0; index < count && !adds.isEmpty(); index++) {
            CardId add = adds.get(index % adds.size());
            removes.stream().filter(card -> !card.equals(add)).findFirst().ifPresent(remove -> {
                swaps.add(new Swap(add, remove));
                removes.remove(remove);
            });
        }
        return swaps;
    }

    private List<CardId> cards(Predicate<CardDefinition> filter) {
        return catalog.all().stream().filter(filter).map(CardDefinition::id)
                .sorted(Comparator.comparing(CardId::value)).toList();
    }

    private static boolean fits(CardDefinition card, Deck deck) {
        return card.faction() == deck.faction() || card.faction() == Faction.NEUTRAL;
    }

    private static int copies(Deck deck, CardId card) {
        return deck.cards().stream().filter(entry -> entry.card().equals(card)).mapToInt(DeckEntry::count).sum();
    }

    private static List<Faction> otherFactions(Faction faction) {
        return Arrays.stream(Faction.values()).filter(other -> other != faction && other != Faction.NEUTRAL)
                .toList();
    }

    private record Swap(CardId added, CardId removed) {}
}
