package fr.daliush.shardbound.dataset.recipe.general;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.scenario.UnitSetup;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import fr.daliush.shardbound.dataset.recipe.Side;
import fr.daliush.shardbound.dataset.recipe.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A spell kills two of the opponent's units with a "Death" ability at once. The spell resolves completely first
 * (9.11), then the abilities resolve in the order their units arrived (9.8). When the asker is low on HP, the first
 * damage ends the game at once and the rest never resolves (1.6).
 */
public final class DeathTriggerOrderRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final int LOW_HP = 2;
    private static final int FULL_HP = 20;

    @Override
    public String interaction() {
        return "death+trigger-order";
    }

    @Override
    public Category category() {
        return Category.MULTI_STEP;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<UnitCard> units = Cards.unitsWith(catalog, Trigger.DEATH);
        List<Source> sweepers = Stream.concat(
                        Cards.spellSources(catalog, Effect.Destroy.class, Side.OPPONENTS).stream(),
                        Cards.spellSources(catalog, Effect.Damage.class, Side.OPPONENTS).stream())
                .filter(source -> source.target().isGroup())
                .toList();
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard first : units) {
            for (UnitCard second : units) {
                if (!first.equals(second)) {
                    for (Source sweeper : sweepers) {
                        for (int hp : List.of(FULL_HP, LOW_HP)) {
                            scenarios.add(scenario(catalog, first, second, sweeper, hp));
                        }
                    }
                }
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard first, UnitCard second, Source sweeper, int hp) {
        GameState start = sweeper.prepare(ScenarioBuilder.of(catalog), ASKER).hp(ASKER, hp)
                .unit(ASKER.opponent(), first.id().value(), setUp -> lethal(setUp, sweeper, first))
                .unit(ASKER.opponent(), second.id().value(), setUp -> lethal(setUp, sweeper, second))
                .deck(ASKER.opponent(), Cards.FILLER, Cards.FILLER)
                .build();
        String question = "My opponent has " + first.name() + ", then " + second.name()
                + ", which arrived after it. I have " + hp + " HP. If I " + sweeper.phrase(first.name())
                + ", what happens?";
        List<String> contextRules = hp == LOW_HP ? List.of("9.8", "9.11", "1.6") : List.of("9.8", "9.11");
        return new Scenario(String.join("/", interaction(), first.id().value(), second.id().value(), sweeper.id(),
                hp + "-hp"), start, List.of(sweeper.play(first.id().value())), ASKER, question,
                new Answer.Events(DeathTriggerOrderRecipe::answers), contextRules);
    }

    /** A damage spell is made lethal for the unit: at most as much defense as it deals. */
    private static UnitSetup lethal(UnitSetup unit, Source sweeper, UnitCard card) {
        return sweeper.effect() instanceof Effect.Damage damage
                ? unit.defense(Math.min(damage.amount(), card.defense()))
                : unit;
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.UnitDestroyed || event instanceof GameEvent.SpellResolved
                || event instanceof GameEvent.AbilityTriggered || event instanceof GameEvent.PlayerDamaged
                || event instanceof GameEvent.CardDrawn || event instanceof GameEvent.GameEnded;
    }
}
