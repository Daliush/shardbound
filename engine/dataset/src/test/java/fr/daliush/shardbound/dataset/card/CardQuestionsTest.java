package fr.daliush.shardbound.dataset.card;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.CATALOG;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.CONTENT;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import java.util.List;
import org.junit.jupiter.api.Test;

class CardQuestionsTest {

    private final CardQuestions questions = new CardQuestions(CATALOG, CONTENT.textTemplates());

    @Test
    void asksAboutEveryCardButTokensWithUniqueIds() {
        List<CardQuestion> all = questions.all();

        assertThat(all).extracting(CardQuestion::id).doesNotHaveDuplicates();
        assertThat(all).extracting(CardQuestion::card).doesNotContain("root.sprout")
                .containsAll(CATALOG.all().stream().filter(card -> !card.isToken())
                        .map(card -> card.id().value()).toList());
        assertThat(all).allSatisfy(question -> {
            assertThat(question.category()).isEqualTo("factual");
            assertThat(question.keyFacts()).isNotEmpty();
        });
    }

    @Test
    void asksAUnitItsFactionCostDefenseAttacksAndEachAttackWithItsEcho() {
        assertThat(of("ember.ash-warden")).extracting(CardQuestion::id).containsExactly(
                "card/ember.ash-warden/faction", "card/ember.ash-warden/cost", "card/ember.ash-warden/defense",
                "card/ember.ash-warden/attacks", "card/ember.ash-warden/attack-1", "card/ember.ash-warden/attack-2");
        assertThat(question("ember.ash-warden", "attacks").keyFacts())
                .containsExactly("Ash Warden has 2 attack abilities: Cinder Bite and Kindle.");
        CardQuestion cinderBite = question("ember.ash-warden", "attack-1");
        assertThat(cinderBite.question()).isEqualTo("What does Ash Warden's Cinder Bite do?");
        assertThat(cinderBite.keyFacts()).containsExactly(
                "Ash Warden's Cinder Bite costs 2 Shards.",
                "Ash Warden's Cinder Bite: \"Deal 4 damage to the target.\"",
                "Ash Warden's Cinder Bite has Echo 50.");
    }

    @Test
    void quotesAbilitiesAsTheCardPrintsThem() {
        assertThat(question("ember.cinderling", "abilities").question())
                .isEqualTo("Besides its attack, what does Cinderling do?");
        assertThat(question("ember.cinderling", "abilities").keyFacts())
                .containsExactly("Cinderling: \"Death: Deal 2 damage to your opponent.\"");
        assertThat(question("tide.coral-font", "abilities").question()).isEqualTo("What does Coral Font do?");
        assertThat(question("tide.coral-font", "abilities").keyFacts())
                .containsExactly("Coral Font: \"All your units have +1/+2.\"");
    }

    @Test
    void givesAFractureSpellOneCostPerStep() {
        assertThat(question("tide.moonpull", "cost").keyFacts()).containsExactly(
                "Moonpull has Fracture 3.",
                "Step 1 of Moonpull costs 1 Shard.",
                "Step 2 of Moonpull costs 2 Shards.",
                "Step 3 of Moonpull costs 3 Shards.");
        assertThat(question("tide.moonpull", "step-2").keyFacts()).containsExactly(
                "Step 2 of Moonpull costs 2 Shards.",
                "Step 2 of Moonpull: \"Give an enemy unit -2/-3.\"");
        assertThat(question("tide.moonpull", "keywords").keyFacts()).containsExactly("Moonpull has Fracture 3.");
    }

    @Test
    void asksASpellItsEffectsKeywordsAndSacrificeCost() {
        assertThat(question("ember.ember-lance", "keywords").keyFacts())
                .containsExactly("Ember Lance has Overcharge.");
        assertThat(question("tide.brinesong", "effects").keyFacts())
                .containsExactly("Brinesong: \"Your opponent discards 1 card of their choice. Draw 1 card.\"");
        assertThat(question("ember.pyre-offering", "sacrifice-cost").keyFacts())
                .containsExactly("To play Pyre Offering, you must sacrifice 1 unit.");
        assertThat(question("neutral.shardling", "faction").keyFacts())
                .containsExactly("Shardling is a neutral card.");
    }

    private List<CardQuestion> of(String card) {
        CardDefinition definition = CATALOG.card(new CardId(card));
        return questions.of(definition);
    }

    private CardQuestion question(String card, String kind) {
        return of(card).stream().filter(question -> question.id().equals("card/" + card + "/" + kind)).findFirst()
                .orElseThrow(() -> new AssertionError("No question card/" + card + "/" + kind));
    }
}
