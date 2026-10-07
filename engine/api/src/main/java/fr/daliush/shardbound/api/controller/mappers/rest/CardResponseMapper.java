package fr.daliush.shardbound.api.controller.mappers.rest;

import fr.daliush.shardbound.api.controller.rest.dto.CardResponse;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.text.CardText;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class CardResponseMapper {

    public CardResponse toResponse(CardDefinition card, CardText text) {
        return switch (card) {
            case UnitCard unit -> response(unit, text, nullable(unit.cost()), unit.defense(), null);
            case SpellCard spell -> response(spell, text, nullable(spell.cost()), null, spell.isFracture()
                    ? IntStream.range(0, spell.fracture().size())
                            .mapToObj(index -> new CardResponse.Step(index + 1, spell.fracture().get(index).cost()))
                            .toList()
                    : null);
            case RelicCard relic -> response(relic, text, relic.cost(), null, null);
        };
    }

    private static CardResponse response(CardDefinition card, CardText text, Integer cost, Integer defense,
                                         List<CardResponse.Step> fracture) {
        return new CardResponse(card.id().value(), card.name(), lower(card.faction()), lower(card.type()), cost,
                defense, card.isToken(), card.keywords().stream().map(CardResponseMapper::lower).toList(),
                text.lines().stream().map(line -> new CardResponse.TextLine(lower(line.kind()), line.text())).toList(),
                card.flavor().orElse(null), fracture);
    }

    private static Integer nullable(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
