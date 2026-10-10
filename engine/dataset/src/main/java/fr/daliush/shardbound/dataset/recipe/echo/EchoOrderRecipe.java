package fr.daliush.shardbound.dataset.recipe.echo;

import static fr.daliush.shardbound.core.scenario.Choices.chooseOrder;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
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

/**
 * The opponent destroys the asker's unit with two Echo attacks: both replay, each with its own X, in the order its
 * owner chooses (11.1.8), each aimed by its owner (11.1.3). The opponent's only unit can die from the first echo, so
 * the order decides what the second one hits. The asker owns the unit, so the asker chooses.
 */
public final class EchoOrderRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P2;

    @Override
    public String interaction() {
        return "echo+order";
    }

    @Override
    public Category category() {
        return Category.MULTI_STEP;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard echo : Cards.unitsWithEcho(catalog)) {
            if (Echoes.echoAttacks(echo) == 2) {
                // The opponent casts it: from their side, the asker's unit is an enemy unit.
                for (Source killer : Cards.spellSources(catalog, Effect.Destroy.class, Side.OPPONENTS)) {
                    if (killer.target().isChosen()) {
                        scenarios.add(scenario(catalog, echo, killer, List.of(0, 1)));
                        scenarios.add(scenario(catalog, echo, killer, List.of(1, 0)));
                    }
                }
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard echo, Source killer, List<Integer> order) {
        String echoCard = echo.id().value();
        PlayerId killerSide = ASKER.opponent();
        GameState start = killer.prepare(ScenarioBuilder.of(catalog), killerSide)
                .unit(ASKER, echoCard)
                .unit(killerSide, Cards.FILLER)
                .build();
        AttackAbility first = echo.attacks().get(order.get(0));
        AttackAbility second = echo.attacks().get(order.get(1));
        String question = "My opponent casts " + ((Source.Spell) killer).card().name() + " on my " + echo.name()
                + ", and their only unit is a " + Cards.fillerName(catalog) + ". If I replay its echoes "
                + first.name().orElseThrow() + " first, then " + second.name().orElseThrow() + ", what happens?";
        String id = String.join("/", interaction(), echoCard, "first-" + order.get(0), killer.id());
        return new Scenario(id, start,
                List.of(killer.play(echoCard), chooseOrder(order.toArray(Integer[]::new))), ASKER, question,
                new Answer.Events(Echoes::isDeathOrEcho), List.of("11.1.8", "11.1.3"));
    }
}
