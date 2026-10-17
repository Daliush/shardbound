package fr.daliush.shardbound.corpus.card;

import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Faction;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.text.CardTextRenderer;
import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * One chunk per card: what it is, then its text as {@link CardTextRenderer} writes it, the text players read. Its
 * name opens the text, so it needs no heading.
 */
public final class CardChunker {

    private CardChunker() {
    }

    /** Every card of the catalog, sorted by id. */
    public static List<Chunk> chunkAllCards(Content content) {
        CardTextRenderer renderer = new CardTextRenderer(content.catalog(), content.textTemplates());
        return content.catalog().all().stream()
                .sorted(Comparator.comparing(card -> card.id().value()))
                .map(card -> chunkCard(card, renderer))
                .toList();
    }

    static Chunk chunkCard(CardDefinition card, CardTextRenderer renderer) {
        String text = writeStatsLine(card) + "\n" + String.join("\n", renderer.render(card).texts());
        return Chunk.card(card.id().value(), card.name(), text, CardRefs.findRefsOfCard(card), sourceFilePath(card));
    }

    /** "Root Sentinel: Root unit, costs 2 Shards, 5 defense." Tokens and Fracture spells have no cost of their own. */
    private static String writeStatsLine(CardDefinition card) {
        List<String> facts = new ArrayList<>();
        facts.add(capitalizedFactionName(card.faction()) + " " + typeNameMarkingTokens(card));
        printedCostIfAny(card).ifPresent(cost -> facts.add("costs " + cost + (cost == 1 ? " Shard" : " Shards")));
        if (card instanceof UnitCard unit) {
            facts.add(unit.defense() + " defense");
        }
        return card.name() + ": " + String.join(", ", facts) + ".";
    }

    /** "Ember", "Tide", "Root", "Neutral". */
    private static String capitalizedFactionName(Faction faction) {
        return faction.name().charAt(0) + faction.name().substring(1).toLowerCase(Locale.ROOT);
    }

    /** "unit", "token unit", "spell", "relic". */
    private static String typeNameMarkingTokens(CardDefinition card) {
        String type = card.type().name().toLowerCase(Locale.ROOT);
        return card.isToken() ? "token " + type : type;
    }

    private static OptionalInt printedCostIfAny(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> unit.cost();
            case SpellCard spell -> spell.cost();
            case RelicCard relic -> OptionalInt.of(relic.cost());
        };
    }

    /** {@code root.root-sentinel} is {@code content/cards/root/root-sentinel.json}. */
    private static String sourceFilePath(CardDefinition card) {
        String id = card.id().value();
        return "content/cards/" + card.faction().name().toLowerCase(Locale.ROOT) + "/"
                + id.substring(id.indexOf('.') + 1) + ".json";
    }
}
