package fr.daliush.shardbound.api.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.daliush.shardbound.core.content.Deck;
import java.util.List;
import java.util.Locale;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeckResponse(String id, String name, String description, String faction, List<Entry> cards) {

    public record Entry(String card, int count) {}

    public static DeckResponse of(Deck deck) {
        return new DeckResponse(deck.id().value(), deck.name(), deck.description().orElse(null),
                deck.faction().name().toLowerCase(Locale.ROOT),
                deck.cards().stream().map(entry -> new Entry(entry.card().value(), entry.count())).toList());
    }
}
