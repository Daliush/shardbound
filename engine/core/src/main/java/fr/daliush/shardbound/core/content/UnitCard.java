package fr.daliush.shardbound.core.content;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

/** {@code cost} is empty for tokens, which are only summoned (8.9). */
public record UnitCard(
        CardId id,
        String name,
        Faction faction,
        OptionalInt cost,
        boolean token,
        int defense,
        List<Keyword> keywords,
        int sacrificeCost,
        List<AttackAbility> attacks,
        List<Ability> abilities,
        Optional<String> flavor) implements CardDefinition {

    public UnitCard {
        keywords = List.copyOf(keywords);
        attacks = List.copyOf(attacks);
        abilities = List.copyOf(abilities);
    }

    @Override
    public CardType type() {
        return CardType.UNIT;
    }

    /** Board places the unit takes (3.4). Always 1 until the card format gets a size. */
    public int size() {
        return 1;
    }

    @Override
    public Stream<Effect> allEffects() {
        return Stream.concat(
                attacks.stream().flatMap(attack -> attack.effects().stream()),
                abilities.stream().flatMap(ability -> ability.effects().stream()));
    }
}
