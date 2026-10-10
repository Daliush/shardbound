package fr.daliush.shardbound.dataset.recipe.general;

import static fr.daliush.shardbound.core.scenario.Pick.graveyardCard;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import java.util.ArrayList;
import java.util.List;

/**
 * Recall takes a unit back from its controller's own graveyard (8.13). With a unit there, it returns to hand; with
 * units only in the opponent's graveyard, the spell has no valid target and does nothing (10.3, 10.5). A full hand
 * cannot happen: playing the spell frees a place in it.
 */
public final class RecallRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "recall";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (SpellCard recall : Cards.spellsWith(catalog, Effect.Recall.class)) {
            scenarios.add(ownGraveyard(catalog, recall));
            scenarios.add(opponentsGraveyard(catalog, recall));
        }
        return scenarios;
    }

    private Scenario ownGraveyard(CardCatalog catalog, SpellCard recall) {
        GameState start = ScenarioBuilder.of(catalog).shards(ASKER, Cards.cost(recall))
                .hand(ASKER, recall.id().value()).graveyard(ASKER, Cards.FILLER).build();
        String question = "My graveyard holds a " + Cards.fillerName(catalog) + ". If I cast " + recall.name()
                + " on it, what happens?";
        return new Scenario(String.join("/", interaction(), "own-graveyard", recall.id().value()), start,
                List.of(Choices.play(recall.id().value()).on(graveyardCard(Cards.FILLER))), ASKER, question,
                new Answer.Events(event -> event instanceof GameEvent.Recalled), List.of());
    }

    private Scenario opponentsGraveyard(CardCatalog catalog, SpellCard recall) {
        GameState start = ScenarioBuilder.of(catalog).shards(ASKER, Cards.cost(recall))
                .hand(ASKER, recall.id().value()).graveyard(ASKER.opponent(), Cards.FILLER).build();
        String question = "My graveyard is empty, and my opponent's holds a " + Cards.fillerName(catalog)
                + ". If I cast " + recall.name() + ", what happens?";
        return new Scenario(String.join("/", interaction(), "opponents-graveyard", recall.id().value()), start,
                List.of(Choices.play(recall.id().value())), ASKER, question,
                new Answer.Events(event -> event instanceof GameEvent.SpellResolved), List.of("8.13", "10.5"));
    }
}
