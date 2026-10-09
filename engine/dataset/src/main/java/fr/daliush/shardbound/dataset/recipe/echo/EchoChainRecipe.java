package fr.daliush.shardbound.dataset.recipe.echo;

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
 * An opponent's Echo unit and one of the asker's are the only units on the board, and a Destroy spell kills the first.
 * Its echo kills the asker's Echo unit, which echoes in turn (11.1.6). A spell that destroys every unit kills both at
 * once, and both echoes resolve in the order of 9.8.
 */
public final class EchoChainRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "echo+chain";
    }

    @Override
    public Category category() {
        return Category.MULTI_STEP;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<UnitCard> echoes = Cards.unitsWithEcho(catalog);
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard theirs : echoes) {
            for (UnitCard mine : echoes) {
                if (!theirs.equals(mine) && Echoes.echoDamage(theirs) > 0) {
                    for (Source killer : Cards.spellSources(catalog, Effect.Destroy.class, Side.OPPONENTS)) {
                        scenarios.add(scenario(catalog, theirs, mine, killer));
                    }
                }
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard theirs, UnitCard mine, Source killer) {
        // The first echo is lethal for the asker's unit, so the chain goes on.
        int defense = Math.min(Echoes.echoDamage(theirs), mine.defense());
        GameState start = killer.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(ASKER.opponent(), theirs.id().value())
                .unit(ASKER, mine.id().value(), setUp -> setUp.defense(defense))
                .build();
        String damaged = defense < mine.defense() ? ", which has " + defense + " defense left," : "";
        String question = "My opponent's " + theirs.name() + " and my " + mine.name() + damaged
                + " are the only units on the board. If I " + killer.phrase(theirs.name()) + ", what happens?";
        List<String> contextRules = killer.target().isGroup() ? List.of("11.1.3", "9.8") : List.of("11.1.3", "11.1.6");
        return new Scenario(String.join("/", interaction(), theirs.id().value(), mine.id().value(), killer.id()),
                start, List.of(killer.play(theirs.id().value())), ASKER, question,
                new Answer.Events(Echoes::isDeathOrEcho), contextRules);
    }
}
