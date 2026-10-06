package fr.daliush.shardbound.core.content;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Every card of the game, by id. */
public final class CardCatalog {

    private final Map<CardId, CardDefinition> cards;

    public CardCatalog(Collection<? extends CardDefinition> cards) {
        Map<CardId, CardDefinition> byId = new LinkedHashMap<>();
        for (CardDefinition card : cards) {
            if (byId.put(card.id(), card) != null) {
                throw new ContentException("Two cards have the id " + card.id());
            }
        }
        this.cards = Collections.unmodifiableMap(byId);
    }

    public CardDefinition card(CardId id) {
        return find(id).orElseThrow(() -> new ContentException("Unknown card " + id));
    }

    public Optional<CardDefinition> find(CardId id) {
        return Optional.ofNullable(cards.get(id));
    }

    public UnitCard unit(CardId id) {
        if (card(id) instanceof UnitCard unit) {
            return unit;
        }
        throw new ContentException(id + " is not a unit");
    }

    public Collection<CardDefinition> all() {
        return cards.values();
    }
}
