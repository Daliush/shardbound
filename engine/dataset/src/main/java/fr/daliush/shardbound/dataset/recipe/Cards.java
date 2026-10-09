package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The cards a recipe builds its scenarios from, found by their data: a new card that fits gets its scenarios without
 * new code. Sorted by id, so the scenarios keep a stable order.
 */
public final class Cards {

    /** A plain unit, no keyword and no ability, for a board that needs one more unit than the interaction names. */
    public static final String FILLER = "neutral.shardling";

    private Cards() {
    }

    /** The units with this keyword, tokens excluded. */
    public static List<UnitCard> unitsWith(CardCatalog catalog, Keyword keyword) {
        return sorted(units(catalog).filter(unit -> unit.has(keyword)));
    }

    /** The cards with a sacrifice cost (6.3, 8.3), tokens excluded. */
    public static List<CardDefinition> withSacrificeCost(CardCatalog catalog) {
        return sorted(catalog.all().stream().filter(card -> card.sacrificeCost() > 0 && !card.isToken()));
    }

    /**
     * What applies an effect of this kind: the spells that have one (Fracture spells and spells with a sacrifice cost
     * excluded, since they need more than a cast), then the attack abilities that have one.
     */
    public static List<Source> sources(CardCatalog catalog, Class<? extends Effect> effect) {
        List<Source> sources = new ArrayList<>();
        for (CardDefinition card : sorted(catalog.all().stream())) {
            switch (card) {
                case SpellCard spell when !spell.isFracture() && spell.sacrificeCost() == 0 ->
                        first(spell.effects(), effect).ifPresent(found ->
                                sources.add(new Source.Spell(spell, found)));
                case UnitCard unit when !unit.token() -> {
                    for (int index = 0; index < unit.attacks().size(); index++) {
                        AttackAbility attack = unit.attacks().get(index);
                        int attackIndex = index;
                        first(attack.effects(), effect).ifPresent(found ->
                                sources.add(new Source.Attack(unit, attackIndex, found)));
                    }
                }
                default -> {
                }
            }
        }
        return sources.stream().sorted(Comparator.comparing(source -> source instanceof Source.Attack)).toList();
    }

    /** The card's printed cost in Shards; a token, which has none, is never played. */
    public static int cost(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> unit.cost().orElseThrow();
            case SpellCard spell -> spell.cost().orElseThrow();
            case RelicCard relic -> relic.cost();
        };
    }

    private static Optional<Effect.Targeted> first(List<Effect> effects, Class<? extends Effect> effect) {
        return effects.stream().filter(effect::isInstance).filter(Effect.Targeted.class::isInstance)
                .map(Effect.Targeted.class::cast).findFirst();
    }

    private static Stream<UnitCard> units(CardCatalog catalog) {
        return catalog.all().stream()
                .filter(card -> card instanceof UnitCard unit && !unit.token())
                .map(UnitCard.class::cast);
    }

    private static <C extends CardDefinition> List<C> sorted(Stream<C> cards) {
        return cards.sorted(Comparator.comparing(card -> card.id().value())).toList();
    }
}
