package fr.daliush.shardbound.dataset.deck;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.CATALOG;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Deck;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DeckQuestionsTest {

    private final DeckQuestions questions = new DeckQuestions(CATALOG, 1L);
    private final List<DeckQuestion> all = questions.all(CONTENT.decks().values());

    @Test
    void asksAboutEveryStarterDeckAndItsMutationsWithUniqueIds() {
        Map<String, Long> perMutation = all.stream()
                .collect(Collectors.groupingBy(DeckQuestion::mutation, Collectors.counting()));

        assertThat(all).extracting(DeckQuestion::id).doesNotHaveDuplicates();
        assertThat(perMutation).containsOnlyKeys("legal", "card-count", "faction", "copies", "token");
        assertThat(perMutation.get("legal")).isEqualTo(3L * CONTENT.decks().size());
        assertThat(all).allSatisfy(question -> assertThat(question.category()).isEqualTo("deck-building"));
    }

    @Test
    void answersAStarterDeckThatItIsLegalUnderEveryRule() {
        DeckQuestion starter = question("deck/ember-starter/legal");

        assertThat(starter.keyFacts()).containsExactly("The deck is legal.");
        assertThat(starter.rules()).containsExactly("2.1", "2.2", "2.3", "2.4");
        assertThat(starter.decklist()).startsWith("Faction: Ember\n2× Ash Warden\n2× Ashborn Drake\n");
        assertThat(starter.decklist().lines()).hasSize(16);
    }

    @Test
    void namesEachProblemTheValidatorFindsWithItsRule() {
        assertThat(all).filteredOn(question -> !question.mutation().equals("legal")).allSatisfy(question -> {
            assertThat(question.keyFacts()).hasSize(2).first().isEqualTo("The deck is not legal.");
            assertThat(question.rules()).hasSize(1);
        });
        assertThat(all).filteredOn(question -> question.mutation().equals("faction")).first()
                .satisfies(question -> assertThat(question.keyFacts().get(1)).matches(
                        "\\w[\\w ]+ is an? (Tide|Root) card, and an Ember deck can only contain Ember and neutral "
                                + "cards\\."));
        assertThat(all).filteredOn(question -> question.mutation().equals("card-count"))
                .extracting(question -> question.keyFacts().get(1)).contains(
                        "The deck has 31 cards, but a deck must contain exactly 30.",
                        "The deck has 29 cards, but a deck must contain exactly 30.");
    }

    @Test
    void addsATokenOnlyToTheDeckOfItsFaction() {
        assertThat(all).filteredOn(question -> question.mutation().equals("token"))
                .isNotEmpty()
                .allSatisfy(question -> {
                    assertThat(question.id()).startsWith("deck/root-starter/token/+root.sprout/");
                    assertThat(question.keyFacts()).contains("Sprout is a token, and tokens are never part of a deck.");
                    assertThat(question.rules()).containsExactly("2.4");
                });
    }

    @Test
    void drawsTheSameDecksFromTheSameSeed() {
        assertThat(new DeckQuestions(CATALOG, 1L).all(CONTENT.decks().values())).isEqualTo(all);
        assertThat(new DeckQuestions(CATALOG, 2L).all(CONTENT.decks().values())).isNotEqualTo(all);
    }

    @Test
    void rejectsAMutationThatBreaksAnotherRuleThanItsOwn() {
        Deck ember = CONTENT.deck("ember-starter");
        MutatedDeck rootToken = MutatedDeck.changed(ember, Mutation.TOKEN, List.of(new CardId("root.sprout")),
                List.of(new CardId("ember.cinderling")));

        assertThatThrownBy(() -> questions.question(rootToken, "Is this deck legal?"))
                .hasMessageContaining("breaks [2.4, 2.2] instead of only 2.4");
    }

    private DeckQuestion question(String id) {
        return all.stream().filter(question -> question.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("No question " + id));
    }
}
