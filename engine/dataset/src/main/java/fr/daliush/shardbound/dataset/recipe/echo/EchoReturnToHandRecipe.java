package fr.daliush.shardbound.dataset.recipe.echo;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
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
 * An opponent's Echo unit is returned to hand: it does not die, so its echo does not trigger (11.1.5). Every Echo unit
 * meets every source of Return to hand that hits the opponent, the spells and the units' attacks.
 */
public final class EchoReturnToHandRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "echo+return-to-hand";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard echo : Cards.unitsWithEcho(catalog)) {
            for (Source source : Cards.sources(catalog, Effect.ReturnToHand.class)) {
                if (Side.hitBy(source.target()).contains(Side.OPPONENTS)) {
                    scenarios.add(scenario(catalog, echo, source));
                }
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard echo, Source source) {
        String echoCard = echo.id().value();
        // The Echo unit is alone on its side, so an attack on it cannot be intercepted.
        GameState start = source.prepare(ScenarioBuilder.of(catalog), ASKER).unit(ASKER.opponent(), echoCard).build();
        String question = "My opponent's " + echo.name() + " is on the board. If I " + source.phrase("it")
                + ", what happens to it?";
        return new Scenario(String.join("/", interaction(), echoCard, source.id()), start,
                List.of(source.play(echoCard)), ASKER, question,
                new Answer.Events(event -> event instanceof GameEvent.ReturnedToHand), List.of("11.1.5"));
    }
}
