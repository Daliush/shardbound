package fr.daliush.shardbound.api.dto;

import fr.daliush.shardbound.core.state.CardInstance;

/** One copy of a card in a game: its instance id and its card id. */
public record CardRef(int id, String card) {

    public static CardRef of(CardInstance card) {
        return new CardRef(card.id().value(), card.card().value());
    }
}
