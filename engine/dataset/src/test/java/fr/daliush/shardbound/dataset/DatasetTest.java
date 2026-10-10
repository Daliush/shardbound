package fr.daliush.shardbound.dataset;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.CONTENT;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.RULEBOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.dataset.card.CardQuestion;
import fr.daliush.shardbound.dataset.deck.DeckQuestion;
import fr.daliush.shardbound.dataset.recipe.Recipes;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetTest {

    @Test
    void buildsTheThreeFilesOfQuestionsTheEngineAndTheCardDataAnswer() {
        Dataset dataset = Dataset.build(CONTENT, RULEBOOK, Recipes.all());

        assertThat(dataset.scenarios()).isNotEmpty();
        assertThat(dataset.cards()).hasSizeGreaterThan(150);
        assertThat(dataset.decks()).hasSizeGreaterThan(40);
    }

    @Test
    void rejectsTwoQuestionsWithOneIdAcrossTheFiles() {
        CardQuestion card = new CardQuestion("same", "factual", "neutral.shardling", "?", List.of("Fact."));
        DeckQuestion deck = new DeckQuestion("same", "deck-building", "legal", "?", "Faction: Ember",
                List.of(), List.of("The deck is legal."), List.of("2.1"));

        assertThatThrownBy(() -> new Dataset(List.of(), List.of(card), List.of(deck)).check(RULEBOOK))
                .hasMessageContaining("Two questions have the id same");
    }

    @Test
    void rejectsADeckRuleThatIsNotInTheRulebook() {
        DeckQuestion deck = new DeckQuestion("deck", "deck-building", "legal", "?", "Faction: Ember",
                List.of(), List.of("The deck is legal."), List.of("2.9"));

        assertThatThrownBy(() -> new Dataset(List.of(), List.of(), List.of(deck)).check(RULEBOOK))
                .hasMessageContaining("rule 2.9 is not in the rulebook");
    }
}
