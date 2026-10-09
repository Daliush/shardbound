package fr.daliush.shardbound.dataset.recipe.fracture;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.FractureStep;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.TargetSpec;
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
import java.util.Collections;
import java.util.List;

/**
 * A Fracture card loses its progress between two steps. It goes to the graveyard instead of returning to a full hand,
 * which a step that draws can fill (11.2.7), or it is discarded by an opponent's spell (11.2.6).
 */
public final class FractureLostProgressRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final int OTHER_CARDS = 9;

    @Override
    public String interaction() {
        return "fracture+lost-progress";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<SpellCard> discarders = Cards.spellsWith(catalog, Effect.Discard.class).stream()
                .filter(spell -> spell.effects().stream().anyMatch(effect -> effect.targets(TargetSpec.OPPONENT)))
                .toList();
        List<Scenario> scenarios = new ArrayList<>();
        for (SpellCard card : Cards.fractureSpells(catalog)) {
            for (int step = 1; step < card.fracture().size(); step++) {
                if (drawsForItsPlayer(card.fracture().get(step - 1))) {
                    scenarios.add(fullHand(catalog, card, step));
                }
            }
            for (SpellCard discarder : discarders) {
                scenarios.add(discarded(catalog, card, discarder));
            }
        }
        return scenarios;
    }

    private Scenario fullHand(CardCatalog catalog, SpellCard card, int step) {
        FractureStep played = card.fracture().get(step - 1);
        ScenarioBuilder board = FractureSteps.board(ScenarioBuilder.of(catalog), ASKER).shards(ASKER, played.cost())
                .hand(ASKER, Collections.nCopies(OTHER_CARDS, Cards.FILLER).toArray(String[]::new));
        GameState start = (step == 1 ? board.hand(ASKER, card.id().value())
                : board.handAtStep(ASKER, card.id().value(), step)).build();
        String question = "My hand is full: " + FractureSteps.inHand(card, step) + ", and " + OTHER_CARDS
                + " other cards. If I play it" + FractureSteps.target(catalog, played) + ", what happens to it?";
        return new Scenario(String.join("/", interaction(), "full-hand", "step-" + step, card.id().value()), start,
                List.of(FractureSteps.play(card, played)), ASKER, question,
                new Answer.Events(event -> event instanceof GameEvent.CardDrawn
                        || event instanceof GameEvent.SentToGraveyardHandFull
                        || event instanceof GameEvent.FractureAdvanced),
                List.of("11.2.7"));
    }

    /** The opponent casts the discard spell on their turn, and the asker's only card is the Fracture card. */
    private Scenario discarded(CardCatalog catalog, SpellCard card, SpellCard discarder) {
        GameState start = ScenarioBuilder.of(catalog).turn(4).active(ASKER.opponent())
                .shards(ASKER.opponent(), Cards.cost(discarder)).hand(ASKER.opponent(), discarder.id().value())
                .deck(ASKER.opponent(), Cards.FILLER, Cards.FILLER)
                .handAtStep(ASKER, card.id().value(), 2)
                .build();
        String question = "My only card in hand is " + FractureSteps.inHand(card, 2) + ". If my opponent casts "
                + discarder.name() + ", what happens to it?";
        return new Scenario(String.join("/", interaction(), "discarded", card.id().value(), discarder.id().value()),
                start, List.of(Choices.play(discarder.id().value())), ASKER, question,
                new Answer.Events(event -> event instanceof GameEvent.CardDiscarded), List.of("11.2.6"));
    }

    private static boolean drawsForItsPlayer(FractureStep step) {
        return step.effects().stream()
                .anyMatch(effect -> effect instanceof Effect.Draw draw && draw.target() == TargetSpec.YOU);
    }
}
