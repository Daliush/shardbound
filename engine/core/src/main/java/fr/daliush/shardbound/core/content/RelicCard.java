package fr.daliush.shardbound.core.content;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record RelicCard(
        CardId id,
        String name,
        Faction faction,
        int cost,
        List<Keyword> keywords,
        int sacrificeCost,
        List<Ability> abilities,
        Optional<String> flavor) implements CardDefinition {

    public RelicCard {
        keywords = List.copyOf(keywords);
        abilities = List.copyOf(abilities);
    }

    @Override
    public CardType type() {
        return CardType.RELIC;
    }

    @Override
    public Stream<Effect> allEffects() {
        return abilities.stream().flatMap(ability -> ability.effects().stream());
    }
}
