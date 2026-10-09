package fr.daliush.shardbound.dataset.recipe.general;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneralRecipesTest {

    @Test
    void deathAbilitiesResolveInArrivalOrderAfterTheSpell() {
        List<DatasetEntry> entries = entries(new DeathTriggerOrderRecipe());
        // 2 arrival orders × (Tempest, Cinderfall) × full or low HP.
        assertThat(entries).hasSize(8);

        DatasetEntry entry = entry(entries,
                "death+trigger-order/tide.shoal-sprite/ember.cinderling/neutral.tempest/50-hp");
        assertThat(entry.keyFacts()).containsSubsequence("Tempest goes to your graveyard.",
                "Shoal Sprite triggers its Death ability.", "Your opponent draws a card.",
                "Cinderling triggers its Death ability.", "You take 2 damage.");
    }

    @Test
    void theGameEndsAtOnceAndTheRestNeverResolves() {
        DatasetEntry entry = entry(entries(new DeathTriggerOrderRecipe()),
                "death+trigger-order/ember.cinderling/tide.shoal-sprite/ember.cinderfall/2-hp");

        assertThat(entry.keyFacts()).endsWith("You take 2 damage.", "You lose: you are at 0 HP.")
                .doesNotContain("Shoal Sprite triggers its Death ability.");
        assertThat(entry.rules()).contains("1.6");
    }

    @Test
    void neitherAFrozenUnitNorOneThatInterceptedCanIntercept() {
        List<DatasetEntry> entries = entries(new FreezeInterceptRecipe());

        assertThat(entries).extracting(DatasetEntry::id, entry -> entry.keyFacts().getFirst()).containsExactly(
                tuple("freeze+intercept/free", "You can intercept with Shardling."),
                tuple("freeze+intercept/already-intercepted",
                        "You cannot intercept with Shardling."),
                tuple("freeze+intercept/frozen/tide.rime-snare",
                        "You cannot intercept with Shardling."));
    }

    @Test
    void anAurasDefenseKeepsAUnitAlive() {
        List<DatasetEntry> entries = entries(new AuraDamageRecipe());
        // (Spark Dart, Cinderfall) × with or without Coral Font.
        assertThat(entries).hasSize(4);

        assertThat(entry(entries, "aura+damage/no-aura/ember.spark-dart").keyFacts())
                .contains("Shardling is destroyed.");
        assertThat(entry(entries, "aura+damage/with-tide.coral-font/ember.spark-dart").keyFacts())
                .containsExactly("Coral Font gives Shardling +1/+2.", "Shardling takes 3 damage.");
    }

    @Test
    void recallOnlyTakesFromItsControllersGraveyard() {
        List<DatasetEntry> entries = entries(new RecallRecipe());

        assertThat(entry(entries, "recall/own-graveyard/ember.rise-from-cinders").keyFacts())
                .containsExactly("Shardling returns from your graveyard to your hand.");
        assertThat(entry(entries, "recall/opponents-graveyard/ember.rise-from-cinders").contextRules())
                .containsExactly("8.13", "10.5");
    }
}
