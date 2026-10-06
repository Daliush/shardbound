package fr.daliush.shardbound.core.content;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** A card as written in {@code content/cards/}: what it is, not where it is in a game. */
public sealed interface CardDefinition permits UnitCard, SpellCard, RelicCard {

    CardId id();

    String name();

    Faction faction();

    CardType type();

    List<Keyword> keywords();

    /** Own units to sacrifice to play the card (8.3), 0 for none. */
    int sacrificeCost();

    Optional<String> flavor();

    /** Every effect printed on the card, wherever it sits. */
    Stream<Effect> allEffects();

    default boolean has(Keyword keyword) {
        return keywords().contains(keyword);
    }

    default boolean isToken() {
        return this instanceof UnitCard unit && unit.token();
    }
}
