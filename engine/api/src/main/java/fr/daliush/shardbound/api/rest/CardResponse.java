package fr.daliush.shardbound.api.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/** A card as the client shows it. Its rendered {@code text} arrives with slice 3. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardResponse(
        String id,
        String name,
        String faction,
        String type,
        Integer cost,
        Integer defense,
        boolean token,
        List<String> keywords,
        String flavor,
        List<Step> fracture) {

    public record Step(int step, int cost) {}

    public static CardResponse of(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> of(unit, nullable(unit.cost()), unit.defense(), null);
            case SpellCard spell -> of(spell, nullable(spell.cost()), null, spell.isFracture()
                    ? IntStream.range(0, spell.fracture().size())
                            .mapToObj(index -> new Step(index + 1, spell.fracture().get(index).cost())).toList()
                    : null);
            case RelicCard relic -> of(relic, relic.cost(), null, null);
        };
    }

    private static CardResponse of(CardDefinition card, Integer cost, Integer defense, List<Step> fracture) {
        return new CardResponse(card.id().value(), card.name(), lower(card.faction()), lower(card.type()), cost,
                defense, card.isToken(), card.keywords().stream().map(CardResponse::lower).toList(),
                card.flavor().orElse(null), fracture);
    }

    private static Integer nullable(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
