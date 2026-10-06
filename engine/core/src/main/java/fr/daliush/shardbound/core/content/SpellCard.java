package fr.daliush.shardbound.core.content;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

/** A spell has either a cost and effects, or Fracture steps (11.2) with their own costs. */
public record SpellCard(
        CardId id,
        String name,
        Faction faction,
        OptionalInt cost,
        List<Keyword> keywords,
        int sacrificeCost,
        List<Effect> effects,
        List<FractureStep> fracture,
        Optional<String> flavor) implements CardDefinition {

    public SpellCard {
        keywords = List.copyOf(keywords);
        effects = List.copyOf(effects);
        fracture = List.copyOf(fracture);
    }

    @Override
    public CardType type() {
        return CardType.SPELL;
    }

    public boolean isFracture() {
        return !fracture.isEmpty();
    }

    @Override
    public Stream<Effect> allEffects() {
        return Stream.concat(effects.stream(), fracture.stream().flatMap(step -> step.effects().stream()));
    }
}
