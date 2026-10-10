package fr.daliush.shardbound.dataset.deck;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A starter deck, possibly changed: {@code id} names the deck, the mutation and the cards it moved. */
record MutatedDeck(String id, Mutation mutation, Deck deck) {

    static MutatedDeck starter(Deck deck) {
        return new MutatedDeck(base(deck, Mutation.LEGAL), Mutation.LEGAL, deck);
    }

    /** The starter deck with one copy more of each card {@code added}, one copy less of each card {@code removed}. */
    static MutatedDeck changed(Deck starter, Mutation mutation, List<CardId> added, List<CardId> removed) {
        List<DeckEntry> entries = new ArrayList<>(starter.cards());
        removed.forEach(card -> change(entries, card, -1));
        added.forEach(card -> change(entries, card, 1));
        String detail = Stream.concat(added.stream().map(card -> "/+" + card.value()),
                removed.stream().map(card -> "/-" + card.value())).collect(Collectors.joining());
        return new MutatedDeck(base(starter, mutation) + detail, mutation,
                new Deck(starter.id(), starter.name(), starter.description(), starter.faction(), entries));
    }

    private static void change(List<DeckEntry> entries, CardId card, int delta) {
        int index = entries.stream().map(DeckEntry::card).toList().indexOf(card);
        if (index < 0) {
            entries.add(new DeckEntry(card, delta));
        } else if (entries.get(index).count() + delta == 0) {
            entries.remove(index);
        } else {
            entries.set(index, new DeckEntry(card, entries.get(index).count() + delta));
        }
    }

    private static String base(Deck deck, Mutation mutation) {
        return "deck/" + deck.id().value() + "/" + mutation.label();
    }
}
