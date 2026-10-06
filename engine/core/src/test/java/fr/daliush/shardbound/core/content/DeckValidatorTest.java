package fr.daliush.shardbound.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.content.json.DeckParser;
import fr.daliush.shardbound.core.testing.TestContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DeckValidatorTest {

    private final DeckValidator validator = new DeckValidator(TestContent.catalog());
    private final Deck emberStarter = TestContent.content().deck("ember-starter");

    @Test
    void acceptsTheStarterDecks() {
        assertThat(validator.problems(emberStarter)).isEmpty();
        assertThat(validator.problems(TestContent.content().deck("root-starter"))).isEmpty();
    }

    @Test
    @DisplayName("2.1 — a deck holds exactly 30 cards")
    void deckSize() {
        Deck tooSmall = withEntries(emberStarter.cards().subList(1, emberStarter.cards().size()));

        assertThat(validator.problems(tooSmall)).containsExactly("has 28 cards, needs exactly 30 (2.1)");
    }

    @Test
    @DisplayName("2.2 — a deck holds cards of its faction and neutral cards only")
    void oneFaction() {
        Deck deck = replaceFirstEntry(new DeckEntry(new CardId("root.mossmender"), 2));

        assertThat(validator.problems(deck))
                .containsExactly("contains root.mossmender, a root card, in a deck of faction ember (2.2)");
    }

    @Test
    @DisplayName("2.3 — a deck holds at most 2 copies of a card, in a single entry")
    void twoCopies() {
        Deck threeCopies = replaceFirstEntry(new DeckEntry(new CardId("ember.cinderling"), 3));
        List<DeckEntry> listedTwice = new ArrayList<>(emberStarter.cards());
        listedTwice.set(0, new DeckEntry(new CardId("ember.cinderling"), 1));
        listedTwice.add(new DeckEntry(new CardId("ember.cinderling"), 1));

        assertThat(validator.problems(threeCopies)).contains("has 3 copies of ember.cinderling, max 2 (2.3)");
        assertThat(validator.problems(withEntries(listedTwice)))
                .containsExactly("lists ember.cinderling 2 times; use one entry with a count (2.3)");
    }

    @Test
    @DisplayName("2.4 — tokens are never part of a deck")
    void noTokens() {
        Deck deck = withEntries(List.of(
                new DeckEntry(new CardId("root.sprout"), 2),
                new DeckEntry(new CardId("root.mossmender"), 2)));

        assertThat(validator.problems(deck))
                .contains("contains the token root.sprout; tokens are never in a deck (2.4)");
    }

    /** The Python content tests check these same cases, so the two checkers cannot drift apart. */
    @TestFactory
    Stream<DynamicTest> agreesWithThePythonChecksOnTheSharedCases() {
        JsonNode cases = JsonMapper.builder().build()
                .readTree(TestContent.contentDir().resolve("tests/fixtures/deck-rules.json"))
                .get("cases");
        return cases.values().stream().map(testCase -> {
            String name = testCase.get("name").stringValue();
            return DynamicTest.dynamicTest(name, () -> {
                Deck deck = new DeckParser().parse(testCase.get("deck"), "deck-rules.json: " + name);
                List<String> expected = testCase.get("problems").values().stream().map(JsonNode::stringValue).toList();
                assertThat(validator.problems(deck)).isEqualTo(expected);
            });
        });
    }

    private Deck replaceFirstEntry(DeckEntry entry) {
        List<DeckEntry> entries = new ArrayList<>(emberStarter.cards());
        entries.set(0, entry);
        return withEntries(entries);
    }

    private Deck withEntries(List<DeckEntry> entries) {
        return new Deck(emberStarter.id(), emberStarter.name(), Optional.empty(), emberStarter.faction(), entries);
    }
}
