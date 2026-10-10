package fr.daliush.shardbound.dataset.recipe.overcharge;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Moves;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import java.util.ArrayList;
import java.util.List;

/**
 * A unit with Overcharge and its attack: Overcharge only lowers the cost of playing the card, never the cost of an
 * attack ability (11.4.5). A legality question, with 2 Shards fewer than the attack costs, then with its cost.
 */
public final class OverchargeAttackCostRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "overcharge+attack-cost";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (CardDefinition card : Cards.withKeyword(catalog, Keyword.OVERCHARGE)) {
            if (card instanceof UnitCard unit) {
                for (int index = 0; index < unit.attacks().size(); index++) {
                    int cost = unit.attacks().get(index).cost();
                    scenarios.add(scenario(catalog, unit, index, Math.max(0, cost - 2)));
                    scenarios.add(scenario(catalog, unit, index, cost));
                }
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard unit, int index, int shards) {
        AttackAbility attack = unit.attacks().get(index);
        String name = attack.name().orElse("attack " + (index + 1));
        GameState start = ScenarioBuilder.of(catalog).shards(ASKER, shards).unit(ASKER, unit.id().value())
                .unit(ASKER.opponent(), Cards.FILLER).build();
        String question = "My " + unit.name() + ", which has Overcharge, has been on the board since my last turn,"
                + " and my opponent has a " + Cards.fillerName(catalog) + ". I have " + shards
                + " Shards. Can I attack with its " + name + "?";
        String attackWith = "attack with " + unit.name() + "'s " + name + ".";
        return new Scenario(String.join("/", interaction(), unit.id().value(), "attack-" + index, shards + "-shards"),
                start, List.of(), ASKER, question,
                new Answer.Legality(Moves.attackingWith(unit.id().value(), index), "You can " + attackWith,
                        "You cannot " + attackWith),
                List.of("11.4.5", "7.2"));
    }
}
