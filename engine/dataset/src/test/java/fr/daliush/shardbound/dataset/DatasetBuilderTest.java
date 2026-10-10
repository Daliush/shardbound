package fr.daliush.shardbound.dataset;

import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.CATALOG;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Moves;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Recipes;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class DatasetBuilderTest {

    @Test
    void everyRecipeGivesScenariosWithAnAnswerKey() {
        List<DatasetEntry> entries = entries(Recipes.all().toArray(Recipe[]::new));

        assertThat(entries).isNotEmpty().allSatisfy(entry -> {
            assertThat(entry.keyFacts()).isNotEmpty().noneMatch(fact -> fact.contains("#"));
            assertThat(entry.rules().isEmpty() && entry.contextRules().isEmpty()).as("essential rules").isFalse();
        });
    }

    @Test
    void rejectsAQuestionThatNamesACardItsAskerCannotSee() {
        GameState start = spark().hand(P2, "neutral.tempest").build();
        Recipe recipe = recipe(scenario("test/hidden", start,
                "My opponent holds Tempest. If I cast Spark Dart on Sprout, what happens?", event -> true, List.of()));

        assertThatThrownBy(() -> entries(recipe)).hasMessageContaining("names Tempest");
    }

    @Test
    void letsAQuestionNameACardItsOpponentPlaysDuringTheScenario() {
        GameState start = ScenarioBuilder.of(CATALOG).active(P2).shards(P2, 1).hand(P2, "ember.spark-dart")
                .unit(P1, "root.sprout").build();
        Scenario scenario = new Scenario("test/revealed", start,
                List.of(play("ember.spark-dart").on(unit("root.sprout"))), P1,
                "My opponent casts Spark Dart on my Sprout. What happens?", new Answer.Events(event -> true),
                List.of());

        assertThat(entries(recipe(scenario))).hasSize(1);
    }

    @Test
    void answersALegalityQuestionWithTheLegalActions() {
        Scenario scenario = new Scenario("test/legality", spark().build(), List.of(), P1, "Can I cast Spark Dart?",
                new Answer.Legality(Moves.playing("ember.spark-dart"), "You can.", "You cannot."), List.of("6.3"));

        assertThat(entries(recipe(scenario))).singleElement()
                .satisfies(entry -> assertThat(entry.keyFacts()).containsExactly("You can."));
    }

    @Test
    void rejectsTwoRecipesThatShareAnInteraction() {
        Scenario first = scenario("test/first", spark().build(), "What happens?", event -> true, List.of());
        Scenario second = scenario("test/second", spark().build(), "What happens?", event -> true, List.of());

        assertThatThrownBy(() -> entries(recipe(first), recipe(second)))
                .hasMessageContaining("Two recipes share the interaction test");
    }

    @Test
    void rejectsAnOutcomeThatStopsOnADecisionNobodyAnswered() {
        GameState start = ScenarioBuilder.of(CATALOG).shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                .unit(P2, "ember.kiln-wyrm").unit(P1, "neutral.shardling").build();
        Scenario scenario = new Scenario("test/unanswered", start,
                List.of(play("neutral.crystal-rupture").on(unit("ember.kiln-wyrm"))), P1, "What happens?",
                new Answer.Events(event -> true), List.of());

        assertThatThrownBy(() -> entries(recipe(scenario)))
                .hasMessageContaining("stops on a CHOOSE_ORDER decision of P2");
    }

    @Test
    void rejectsAScenarioWhoseOutcomeAnswersNothing() {
        Recipe recipe = recipe(scenario("test/nothing", spark().build(), "What happens?",
                event -> event instanceof GameEvent.AnchorPrevented, List.of()));

        assertThatThrownBy(() -> entries(recipe)).hasMessageContaining("no event answers the question");
    }

    @Test
    void rejectsAContextRuleThatIsNotInTheRulebook() {
        Recipe recipe = recipe(scenario("test/typo", spark().build(), "What happens?", event -> true,
                List.of("8.99")));

        assertThatThrownBy(() -> entries(recipe)).hasMessageContaining("rule 8.99 is not in the rulebook");
    }

    @Test
    void rejectsTwoScenariosWithTheSameId() {
        Scenario scenario = scenario("test/twice", spark().build(), "What happens?", event -> true, List.of());

        assertThatThrownBy(() -> entries(recipe(scenario, scenario)))
                .hasMessageContaining("Two scenarios have the id test/twice");
    }

    private static ScenarioBuilder spark() {
        return ScenarioBuilder.of(CATALOG).shards(P1, 1).hand(P1, "ember.spark-dart").unit(P2, "root.sprout");
    }

    private static Scenario scenario(String id, GameState start, String question, Predicate<GameEvent> answers,
            List<String> contextRules) {
        return new Scenario(id, start, List.of(play("ember.spark-dart").on(unit("root.sprout"))), P1, question,
                new Answer.Events(answers), contextRules);
    }

    private static Recipe recipe(Scenario... scenarios) {
        return new Recipe() {
            @Override
            public String interaction() {
                return "test";
            }

            @Override
            public Category category() {
                return Category.INTERACTION;
            }

            @Override
            public List<Scenario> scenarios(CardCatalog catalog) {
                return List.of(scenarios);
            }
        };
    }
}
