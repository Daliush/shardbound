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
import fr.daliush.shardbound.dataset.recipe.Category;
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
            assertThat(entry.keyFacts()).isNotEmpty();
            assertThat(entry.rules()).isNotEmpty();
            assertThat(entry.keyFacts()).noneMatch(fact -> fact.contains("#"));
        });
    }

    @Test
    void rejectsAQuestionThatNamesACardItsAskerCannotSee() {
        GameState start = spark().hand(P2, "neutral.tempest").build();
        Recipe recipe = recipe(List.of(scenario("test/hidden", start,
                "My opponent holds Tempest. If I cast Spark Dart on Sprout, what happens?", List.of())), event -> true);

        assertThatThrownBy(() -> entries(recipe)).hasMessageContaining("names Tempest");
    }

    @Test
    void rejectsAScenarioWhoseOutcomeAnswersNothing() {
        Recipe recipe = recipe(List.of(scenario("test/nothing", spark().build(), "What happens?", List.of())),
                event -> event instanceof GameEvent.AnchorPrevented);

        assertThatThrownBy(() -> entries(recipe)).hasMessageContaining("no event answers the question");
    }

    @Test
    void rejectsAContextRuleThatIsNotInTheRulebook() {
        Recipe recipe = recipe(List.of(scenario("test/typo", spark().build(), "What happens?", List.of("8.99"))),
                event -> true);

        assertThatThrownBy(() -> entries(recipe)).hasMessageContaining("rule 8.99 is not in the rulebook");
    }

    @Test
    void rejectsTwoScenariosWithTheSameId() {
        Scenario scenario = scenario("test/twice", spark().build(), "What happens?", List.of());

        assertThatThrownBy(() -> entries(recipe(List.of(scenario, scenario), event -> true)))
                .hasMessageContaining("Two scenarios have the id test/twice");
    }

    private static ScenarioBuilder spark() {
        return ScenarioBuilder.of(CATALOG).shards(P1, 1).hand(P1, "ember.spark-dart").unit(P2, "root.sprout");
    }

    private static Scenario scenario(String id, GameState start, String question, List<String> contextRules) {
        return new Scenario(id, start, List.of(play("ember.spark-dart").on(unit("root.sprout"))), P1, question,
                contextRules);
    }

    private static Recipe recipe(List<Scenario> scenarios, Predicate<GameEvent> isKey) {
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
                return scenarios;
            }

            @Override
            public boolean isKey(GameEvent event) {
                return isKey.test(event);
            }
        };
    }
}
