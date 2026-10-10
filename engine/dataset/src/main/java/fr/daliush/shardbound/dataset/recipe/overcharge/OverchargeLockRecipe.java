package fr.daliush.shardbound.dataset.recipe.overcharge;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choice;
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
import java.util.stream.Collectors;

/**
 * Overcharged cards cost 2 Shards less now and lock 2 of the player's Shards on their next turn (11.4.1, 11.4.2).
 * Each card alone, then all of them together, whose locks stack (11.4.3); with few Shards, the locks exceed the
 * refill, and the excess is lost, not carried over (11.4.4).
 */
public final class OverchargeLockRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final int ENOUGH = 5;
    private static final int FEW = 2;

    @Override
    public String interaction() {
        return "overcharge+locked-shards";
    }

    @Override
    public Category category() {
        return Category.MULTI_STEP;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<CardDefinition> cards = Cards.withKeyword(catalog, Keyword.OVERCHARGE);
        List<Scenario> scenarios = new ArrayList<>();
        for (CardDefinition card : cards) {
            scenarios.add(scenario(catalog, "one/" + card.id().value(), List.of(card), ENOUGH, 1));
        }
        if (cards.size() > 1) {
            scenarios.add(scenario(catalog, "stacked", cards, ENOUGH, 1));
            scenarios.add(scenario(catalog, "excess", cards, FEW, 2));
        }
        return scenarios;
    }

    /** Plays every card overcharged, then lets {@code turnsAfter} turns of the asker's come. */
    private Scenario scenario(CardCatalog catalog, String variant, List<CardDefinition> cards, int shards,
            int turnsAfter) {
        ScenarioBuilder board = ScenarioBuilder.of(catalog).shards(ASKER, shards).maxShards(ASKER, shards)
                .deck(ASKER, Cards.FILLER, Cards.FILLER, Cards.FILLER)
                .deck(ASKER.opponent(), Cards.FILLER, Cards.FILLER, Cards.FILLER);
        cards.forEach(card -> board.hand(ASKER, card.id().value()));
        List<Choice> choices = new ArrayList<>();
        cards.forEach(card -> choices.add(Choices.play(card.id().value()).overcharged()));
        for (int turn = 0; turn < turnsAfter; turn++) {
            choices.add(endTurn());
            choices.add(endTurn());
        }
        String played = cards.stream().map(CardDefinition::name).collect(Collectors.joining(" and "));
        String question = "I have " + shards + " Shards out of " + shards + ". If I play " + played
                + (cards.size() > 1 ? ", all overcharged," : " overcharged") + " this turn, what happens to my Shards"
                + (turnsAfter == 1 ? " this turn and on my next turn?" : " on my next " + turnsAfter + " turns?");
        GameState start = board.build();
        List<String> contextRules = shards == FEW ? List.of("11.4.1", "11.4.4") : List.of("11.4.1");
        return new Scenario(String.join("/", interaction(), variant), start, choices, ASKER, question,
                new Answer.Events(OverchargeLockRecipe::isAboutTheAsker), contextRules);
    }

    /** The cards the asker plays, their locks and their next refills; not the opponent's turn. */
    private static boolean isAboutTheAsker(GameEvent event) {
        return switch (event) {
            case GameEvent.CardPlayed played -> played.player() == ASKER;
            case GameEvent.ShardsLocked locked -> locked.player() == ASKER;
            case GameEvent.ShardsRefilled refilled -> refilled.player() == ASKER;
            default -> false;
        };
    }
}
