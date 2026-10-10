package fr.daliush.shardbound.dataset;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.CONTENT;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.RULEBOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.dataset.card.CardQuestion;
import fr.daliush.shardbound.dataset.recipe.Recipes;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetTest {

    @Test
    void buildsTheFilesOfQuestionsTheEngineAndTheCardDataAnswer() {
        Dataset dataset = Dataset.build(CONTENT, RULEBOOK, Recipes.all());

        assertThat(dataset.scenarios()).isNotEmpty();
        assertThat(dataset.cards()).hasSizeGreaterThan(150);
    }

    @Test
    void rejectsTwoQuestionsWithOneIdAcrossTheFiles() {
        DatasetEntry scenario = new DatasetEntry("same", "interaction", "test", "?", List.of(), List.of("Fact."),
                List.of("8.1"), List.of(), List.of());
        CardQuestion card = new CardQuestion("same", "factual", "neutral.shardling", "?", List.of("Fact."));

        assertThatThrownBy(() -> new Dataset(List.of(scenario), List.of(card)).check(RULEBOOK))
                .hasMessageContaining("Two questions have the id same");
    }
}
