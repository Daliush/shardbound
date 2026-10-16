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
                "death+trigger-order/tide.shoal-sprite/ember.cinderling/neutral.tempest/20-hp");
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

    @Test
    void anAuraThatEndsLowersTheMaxAndOnlyWhatIsAboveIt() {
        List<DatasetEntry> entries = entries(new AuraEndRecipe());

        assertThat(entries).hasSize(2).allSatisfy(entry -> assertThat(entry.keyFacts())
                .contains("Coral Font is destroyed.", "Shardling has 3 defense, out of 3."));
        assertThat(entry(entries, "aura+end/full/tide.coral-font/neutral.shardbreak-pulse").question())
                .contains("boosts to 5 defense, out of 5");
    }

    @Test
    void aBonusThatEndsCapsTheDefenseAndAMalusThatEndsGivesItBack() {
        List<DatasetEntry> entries = entries(new ModifyExpiryRecipe());
        assertThat(entries).hasSize(4);

        assertThat(entry(entries, "modify+expiry/bonus-damaged/tide.brine-ward").keyFacts())
                .as("1/3, then 4/6, then 3/3").endsWith("Shardling has 3 defense, out of 3.");
        DatasetEntry malus = entry(entries, "modify+expiry/malus-then-hit/tide.riptide-hex/ember.spark-dart");
        assertThat(malus.keyFacts()).as("9/9, 6/6, 3/6, then 6/9").endsWith("Shard Construct has 6 defense, out of 9.");
        assertThat(malus.rules()).contains("8.17");
        assertThat(entry(entries, "modify+expiry/lethal-malus/tide.riptide-hex").keyFacts())
                .contains("Shardling is destroyed.");
    }

    @Test
    void anAttackAbilityResolvesBeforeTheAttackAndTheIntercept() {
        List<DatasetEntry> entries = entries(new AttackAbilityRecipe());
        assertThat(entries).hasSize(3);

        assertThat(entry(entries, "attack-ability/fragile-target/ember.ashfall-raider:searing-rush").keyFacts())
                .as("the target dies first: the attack does nothing to it").containsExactly(
                        "Ashfall Raider triggers its On attack ability.", "Shardling takes 1 damage.",
                        "Shardling is destroyed.");
        assertThat(entry(entries, "attack-ability/fragile-interceptor/ember.ashfall-raider:searing-rush")
                .keyFacts()).endsWith("Shardling is destroyed.", "Shard Construct takes 4 damage.");
    }

    @Test
    void aSacrificeEffectNeedsItsUnitsAndAnAnchoredOneStays() {
        List<DatasetEntry> entries = entries(new SacrificeEffectRecipe());
        // No unit, one unit, two units, and each of the 3 Anchor units still protected.
        assertThat(entries).hasSize(6);

        assertThat(entry(entries, "sacrifice-effect/no-unit/ember.cinder-pact").keyFacts())
                .containsExactly("You cannot cast Cinder Pact.");
        assertThat(entry(entries, "sacrifice-effect/protected-anchor/ember.cinder-pact/root.root-sentinel")
                .keyFacts()).containsExactly(
                        "Root Sentinel is anchored: it stays on the board, and the sacrifice counts as paid.",
                        "Your opponent takes 6 damage.");
    }
}
