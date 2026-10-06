package fr.daliush.shardbound.api.controller.mappers.rest;

import fr.daliush.shardbound.api.controller.rest.dto.CardResponse;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class CardResponseMapper {

    public CardResponse toResponse(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> response(unit, nullable(unit.cost()), unit.defense(), null);
            case SpellCard spell -> response(spell, nullable(spell.cost()), null, spell.isFracture()
                    ? IntStream.range(0, spell.fracture().size())
                            .mapToObj(index -> new CardResponse.Step(index + 1, spell.fracture().get(index).cost()))
                            .toList()
                    : null);
            case RelicCard relic -> response(relic, relic.cost(), null, null);
        };
    }

    private static CardResponse response(CardDefinition card, Integer cost, Integer defense,
                                         List<CardResponse.Step> fracture) {
        return new CardResponse(card.id().value(), card.name(), lower(card.faction()), lower(card.type()), cost,
                defense, card.isToken(), card.keywords().stream().map(CardResponseMapper::lower).toList(),
                card.flavor().orElse(null), fracture);
    }

    private static Integer nullable(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
