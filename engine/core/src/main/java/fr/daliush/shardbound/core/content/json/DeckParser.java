package fr.daliush.shardbound.core.content.json;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckEntry;
import fr.daliush.shardbound.core.content.DeckId;
import fr.daliush.shardbound.core.content.Faction;
import tools.jackson.databind.JsonNode;

/** Turns a deck file of {@code content/decks/} into a {@link Deck}. Deck-building rules are checked elsewhere. */
public final class DeckParser {

    public Deck parse(JsonNode node, String location) {
        JsonObjectReader json = new JsonObjectReader(node, location);
        Faction faction = json.enumValue("faction", Faction.class);
        if (faction == Faction.NEUTRAL) {
            throw json.error("a deck belongs to Ember, Tide or Root (2.2)");
        }
        Deck deck = new Deck(new DeckId(json.string("id")), json.string("name"), json.optionalString("description"),
                faction, json.objects("cards").stream().map(DeckParser::entry).toList());
        json.finish();
        return deck;
    }

    private static DeckEntry entry(JsonObjectReader json) {
        DeckEntry entry = new DeckEntry(new CardId(json.string("card")), json.integer("count", 1, Integer.MAX_VALUE));
        json.finish();
        return entry;
    }
}
