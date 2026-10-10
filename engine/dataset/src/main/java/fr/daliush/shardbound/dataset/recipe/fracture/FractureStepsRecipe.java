package fr.daliush.shardbound.dataset.recipe.fracture;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.FractureStep;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choice;
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
import java.util.Locale;

/**
 * A Fracture card played step by step. Each step applies its effect, then the card returns to hand, until the last
 * step sends it to the graveyard (11.2.2, 11.2.4). Two steps cannot be played in the same turn, with no limit on the
 * delay between them (11.2.3): a legality question, this turn or the next.
 */
public final class FractureStepsRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    /** When the asker wants to play the next step, as the question and the answer say it. */
    private enum When {
        THIS_TURN("this turn", "this turn"),
        NEXT_TURN("on my next turn", "on your next turn");

        private final String asked;
        private final String answered;

        When(String asked, String answered) {
            this.asked = asked;
            this.answered = answered;
        }

        String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    @Override
    public String interaction() {
        return "fracture+steps";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (SpellCard card : Cards.fractureSpells(catalog)) {
            for (int step = 1; step <= card.fracture().size(); step++) {
                scenarios.add(stepPlayed(catalog, card, step));
            }
            for (When when : When.values()) {
                scenarios.add(nextStepLegal(catalog, card, when));
            }
        }
        return scenarios;
    }

    private Scenario stepPlayed(CardCatalog catalog, SpellCard card, int step) {
        FractureStep played = card.fracture().get(step - 1);
        ScenarioBuilder board = FractureSteps.board(ScenarioBuilder.of(catalog), ASKER).shards(ASKER, played.cost());
        GameState start = (step == 1 ? board.hand(ASKER, card.id().value())
                : board.handAtStep(ASKER, card.id().value(), step)).build();
        String question = played.effects().stream().map(Cards::turnFor).filter(turn -> !turn.isEmpty()).findFirst()
                .orElse("") + "I have " + FractureSteps.inHand(card, step) + ", in my hand. I have a "
                + Cards.fillerName(catalog) + " and my opponent has a " + FractureSteps.enemyName(catalog)
                + ". If I play it" + FractureSteps.target(catalog, played) + ", what happens?";
        boolean last = step == card.fracture().size();
        return new Scenario(String.join("/", interaction(), "step-" + step, card.id().value()), start,
                List.of(FractureSteps.play(card, played)), ASKER, question,
                new Answer.Events(FractureStepsRecipe::answers), last ? List.of("11.2.4") : List.of());
    }

    private Scenario nextStepLegal(CardCatalog catalog, SpellCard card, When when) {
        FractureStep first = card.fracture().getFirst();
        FractureStep second = card.fracture().get(1);
        // Enough Shards for both steps, this turn and the next, so only 11.2.3 can forbid the second one.
        int shards = first.cost() + second.cost();
        GameState start = FractureSteps.board(ScenarioBuilder.of(catalog), ASKER)
                .shards(ASKER, shards).maxShards(ASKER, shards).hand(ASKER, card.id().value()).build();
        List<Choice> choices = when == When.THIS_TURN
                ? List.of(FractureSteps.play(card, first))
                : List.of(FractureSteps.play(card, first), endTurn(), endTurn());
        String question = "I play the first step of " + card.name() + FractureSteps.target(catalog, first)
                + " this turn, and it returns to my hand. I have the Shards for its next step."
                + " Can I play its next step " + when.asked + "?";
        String nextStep = "play the next step of " + card.name() + " " + when.answered + ".";
        return new Scenario(String.join("/", interaction(), "next-step-" + when.label(), card.id().value()), start,
                choices, ASKER, question,
                new Answer.Legality(Moves.playing(card.id().value()), "You can " + nextStep, "You cannot " + nextStep),
                List.of("11.2.3"));
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.FractureAdvanced || event instanceof GameEvent.SpellResolved
                || event instanceof GameEvent.Modified || event instanceof GameEvent.CardDrawn
                || event instanceof GameEvent.Frozen || event instanceof GameEvent.ReturnedToHand
                || event instanceof GameEvent.UnitDestroyed;
    }
}
