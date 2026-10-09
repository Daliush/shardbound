package fr.daliush.shardbound.dataset.recipe.general;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Pick.unit;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.scenario.Choice;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.scenario.UnitSetup;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Moves;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import fr.daliush.shardbound.dataset.recipe.Side;
import fr.daliush.shardbound.dataset.recipe.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The opponent attacks one of the asker's units: can the asker's other unit intercept? Yes when it is free; no when it
 * is frozen (8.10) or has already intercepted this turn (7.5). The asker is the defender; each freeze spell of the
 * opponent gives one scenario.
 */
public final class FreezeInterceptRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P2;
    private static final String ATTACKER = "ember.cinderling";
    private static final String TARGET = "neutral.shard-construct";

    @Override
    public String interaction() {
        return "freeze+intercept";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        String interceptor = Cards.fillerName(catalog);
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(scenario(catalog, "free", setUp -> setUp, List.of(), "", List.of("7.5")));
        scenarios.add(scenario(catalog, "already-intercepted", UnitSetup::hasIntercepted, List.of(),
                "My " + interceptor + " has already intercepted an attack this turn. ", List.of("7.5")));
        // The opponent casts the freeze: from their side, the asker's unit is an enemy unit.
        for (Source freeze : Cards.spellSources(catalog, Effect.Freeze.class, Side.OPPONENTS)) {
            if (freeze.target().isChosen()) {
                scenarios.add(scenario(catalog, "frozen/" + freeze.id(), setUp -> setUp, List.of(freeze),
                        "My opponent casts " + freezeName(freeze) + " on my " + interceptor + ". ",
                        List.of("7.5", "8.10")));
            }
        }
        return scenarios;
    }

    /** {@code freezes} are played by the opponent on the interceptor before the attack. */
    private Scenario scenario(CardCatalog catalog, String variant, UnaryOperator<UnitSetup> interceptorSetUp,
            List<Source> freezes, String before, List<String> contextRules) {
        PlayerId attacker = ASKER.opponent();
        ScenarioBuilder board = ScenarioBuilder.of(catalog).active(attacker)
                .unit(attacker, ATTACKER)
                .unit(ASKER, TARGET)
                .unit(ASKER, Cards.FILLER, interceptorSetUp)
                .deck(attacker, Cards.FILLER);
        int shards = attackCost(catalog);
        for (Source freeze : freezes) {
            freeze.place(board, attacker);
            shards += freeze.cost();
        }
        GameState start = board.shards(attacker, shards).build();
        List<Choice> choices = new ArrayList<>();
        freezes.forEach(freeze -> choices.add(freeze.play(Cards.FILLER)));
        choices.add(attack(ATTACKER).on(unit(TARGET)));
        String interceptor = Cards.fillerName(catalog);
        String question = before + (before.isEmpty() ? "My opponent" : "Then my opponent") + " attacks my "
                + name(catalog, TARGET) + " with " + name(catalog, ATTACKER) + ". Can I intercept with my "
                + interceptor + "?";
        return new Scenario(String.join("/", interaction(), variant), start, choices, ASKER, question,
                new Answer.Legality(Moves.interceptingWith(Cards.FILLER), "You can intercept with " + interceptor
                        + ".", "You cannot intercept with " + interceptor + "."),
                contextRules);
    }

    private static int attackCost(CardCatalog catalog) {
        return catalog.unit(new CardId(ATTACKER)).attacks().getFirst().cost();
    }

    private static String name(CardCatalog catalog, String card) {
        return catalog.card(new CardId(card)).name();
    }

    private static String freezeName(Source freeze) {
        return ((Source.Spell) freeze).card().name();
    }
}
