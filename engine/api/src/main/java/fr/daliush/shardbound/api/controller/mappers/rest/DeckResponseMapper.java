package fr.daliush.shardbound.api.controller.mappers.rest;

import fr.daliush.shardbound.api.controller.rest.dto.DeckResponse;
import fr.daliush.shardbound.core.content.Deck;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DeckResponseMapper {

    public DeckResponse toResponse(Deck deck) {
        return new DeckResponse(deck.id().value(), deck.name(), deck.description().orElse(null),
                deck.faction().name().toLowerCase(Locale.ROOT),
                deck.cards().stream().map(entry -> new DeckResponse.Entry(entry.card().value(), entry.count()))
                        .toList());
    }
}
