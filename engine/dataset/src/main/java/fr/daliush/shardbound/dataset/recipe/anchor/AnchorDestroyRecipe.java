package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.core.scenario.Pick.unit;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choice;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.scenario.UnitSetup;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import fr.daliush.shardbound.dataset.recipe.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A spell destroys an Anchored unit. While protected, the unit stays and the rest of the spell applies (11.3.2,
 * 11.3.3); once its protection is over, it is destroyed (11.3.6). Every Anchor unit meets every Destroy spell, on each
 * side the spell can hit, protected or not.
 */
public final class AnchorDestroyRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    private enum Protection {
        PROTECTED(List.of("11.3.1")),
        EXPIRED(List.of("11.3.1", "11.3.6"));

        /** Why the unit is, or is no longer, protected. */
        private final List<String> contextRules;

        Protection(List<String> contextRules) {
            this.contextRules = contextRules;
        }

        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public String interaction() {
        return "anchor+destroy";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard anchored : Cards.unitsWith(catalog, Keyword.ANCHOR)) {
            for (SpellCard spell : Cards.spellsWith(catalog, Effect.Destroy.class)) {
                for (Side side : Side.hitBy(target(spell))) {
                    for (Protection protection : Protection.values()) {
                        scenarios.add(scenario(catalog, anchored, spell, side, protection));
                    }
                }
            }
        }
        return scenarios;
    }

    @Override
    public boolean isKey(GameEvent event) {
        return event instanceof GameEvent.AnchorPrevented || event instanceof GameEvent.UnitDestroyed;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard anchored, SpellCard spell, Side side,
            Protection protection) {
        GameState start = ScenarioBuilder.of(catalog)
                .shards(ASKER, spell.cost().orElseThrow())
                .hand(ASKER, spell.id().value())
                .unit(side.of(ASKER), anchored.id().value(), unit -> setUp(unit, side, protection))
                .build();
        Choices.PlayChoice cast = Choices.play(spell.id().value());
        Choice play = target(spell).isChosen() ? cast.on(unit(anchored.id().value())) : cast;
        String id = String.join("/", interaction(), side.label(), protection.label(), anchored.id().value(),
                spell.id().value());
        return new Scenario(id, start, List.of(play), ASKER, question(anchored, spell, side, protection),
                protection.contextRules);
    }

    /** 11.3.1: protected from its arrival until its controller's next turn starts. */
    private static UnitSetup setUp(UnitSetup unit, Side side, Protection protection) {
        return switch (protection) {
            case PROTECTED -> side == Side.MINE ? unit.arrivedThisTurn().anchorProtected() : unit.anchorProtected();
            case EXPIRED -> unit;
        };
    }

    private static String question(UnitCard anchored, SpellCard spell, Side side, Protection protection) {
        String unit = anchored.name();
        String situation = switch (side) {
            case MINE -> protection == Protection.PROTECTED
                    ? "I played " + unit + " this turn."
                    : "My " + unit + " has been on the board since my last turn.";
            case OPPONENTS -> protection == Protection.PROTECTED
                    ? "My opponent played " + unit + " on their last turn."
                    : "My opponent's " + unit + " has been on the board since before their last turn.";
        };
        String cast = target(spell).isChosen() ? "If I cast " + spell.name() + " on it"
                : "If I cast " + spell.name();
        return situation + " " + cast + ", what happens to it?";
    }

    private static TargetSpec target(SpellCard spell) {
        return Cards.effect(spell, Effect.Destroy.class).target();
    }
}
