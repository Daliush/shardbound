package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The cards a recipe builds its scenarios from, found by their data: a new card that fits gets its scenarios without
 * new code. Sorted by id, so the scenarios keep a stable order.
 */
public final class Cards {

    private Cards() {
    }

    /** The units with this keyword, tokens excluded. */
    public static List<UnitCard> unitsWith(CardCatalog catalog, Keyword keyword) {
        return sorted(catalog.all().stream()
                .filter(card -> card instanceof UnitCard unit && !unit.token() && unit.has(keyword))
                .map(UnitCard.class::cast));
    }

    /** The spells, Fracture spells excluded, with an effect of this kind. */
    public static List<SpellCard> spellsWith(CardCatalog catalog, Class<? extends Effect> effect) {
        return sorted(catalog.all().stream()
                .filter(card -> card instanceof SpellCard spell && !spell.isFracture()
                        && spell.effects().stream().anyMatch(effect::isInstance))
                .map(SpellCard.class::cast));
    }

    /** The spell's first effect of this kind. */
    public static <E extends Effect> E effect(SpellCard spell, Class<E> effect) {
        return spell.effects().stream().filter(effect::isInstance).map(effect::cast).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(spell.id() + " has no " + effect.getSimpleName()));
    }

    private static <C extends CardDefinition> List<C> sorted(Stream<C> cards) {
        return cards.sorted(Comparator.comparing(card -> card.id().value())).toList();
    }
}
