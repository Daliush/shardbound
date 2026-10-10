package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.content.Ability;
import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.content.Trigger;
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

    /** The filler's name, as a question says it. */
    public static String fillerName(CardCatalog catalog) {
        return catalog.card(new CardId(FILLER)).name();
    }

    /** The units with this keyword, tokens excluded. */
    public static List<UnitCard> unitsWith(CardCatalog catalog, Keyword keyword) {
        return sorted(units(catalog).filter(unit -> unit.has(keyword)));
    }

    /** The units with an Echo attack ability (11.1), tokens excluded. */
    public static List<UnitCard> unitsWithEcho(CardCatalog catalog) {
        return sorted(units(catalog).filter(unit -> unit.attacks().stream()
                .anyMatch(attack -> attack.echo().isPresent())));
    }

    /** The units with an ability of this trigger (section 9), tokens excluded. */
    public static List<UnitCard> unitsWith(CardCatalog catalog, Trigger trigger) {
        return sorted(units(catalog).filter(unit -> unit.abilities().stream()
                .anyMatch(ability -> ability.trigger() == trigger)));
    }

    /** The cards with this keyword, tokens excluded. */
    public static List<CardDefinition> withKeyword(CardCatalog catalog, Keyword keyword) {
        return sorted(catalog.all().stream().filter(card -> card.has(keyword) && !card.isToken()));
    }

    /** The cards with a continuous stat aura (8.14) on their controller's units. */
    public static List<CardDefinition> withStatAura(CardCatalog catalog) {
        return sorted(catalog.all().stream().filter(card -> abilities(card).stream()
                .anyMatch(ability -> ability.trigger() == Trigger.CONTINUOUS && ability.effects().stream()
                        .anyMatch(effect -> effect instanceof Effect.StatAura aura
                                && aura.target() == TargetSpec.ALL_ALLY_UNITS))));
    }

    /** The Fracture spells (11.2). */
    public static List<SpellCard> fractureSpells(CardCatalog catalog) {
        return sorted(catalog.all().stream()
                .filter(card -> card instanceof SpellCard spell && spell.isFracture())
                .map(SpellCard.class::cast));
    }

    /** The spells, Fracture spells excluded, with an effect of this kind, whatever its target. */
    public static List<SpellCard> spellsWith(CardCatalog catalog, Class<? extends Effect> effect) {
        return sorted(catalog.all().stream()
                .filter(card -> card instanceof SpellCard spell && !spell.isFracture()
                        && spell.effects().stream().anyMatch(effect::isInstance))
                .map(SpellCard.class::cast));
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

    /** The spells among {@link #sources} whose target can hit the units of this side. */
    public static List<Source> spellSources(CardCatalog catalog, Class<? extends Effect> effect, Side side) {
        return sources(catalog, effect).stream()
                .filter(source -> source instanceof Source.Spell && Side.hitBy(source.target()).contains(side))
                .toList();
    }

    /**
     * "It is turn 3, my turn. " before a question whose answer names a turn: a freeze lasts "until the end of turn 4"
     * (8.10), which the asker can only work out from the current one. Scenarios start on turn 3, the asker's.
     */
    public static String turnFor(Effect effect) {
        return effect instanceof Effect.Freeze ? "It is turn 3, my turn. " : "";
    }

    /** The card's printed cost in Shards; a token, which has none, is never played. */
    public static int cost(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> unit.cost().orElseThrow();
            case SpellCard spell -> spell.cost().orElseThrow();
            case RelicCard relic -> relic.cost();
        };
    }

    private static List<Ability> abilities(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> unit.abilities();
            case RelicCard relic -> relic.abilities();
            case SpellCard ignored -> List.of();
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
