package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnchorDoomRecipeTest {

    private final List<DatasetEntry> entries = entries(new AnchorDoomRecipe());

    @Test
    void meetsEveryAnchorUnitWithEveryDamageSpellThatHitsTheOpponent() {
        // 2 Anchor units × (Spark Dart + Cinderfall) × protected or expired.
        assertThat(entries).hasSize(8);
    }

    @Test
    void aProtectedUnitIsDoomedThenDestroyedAtTheEndOfTheTurnItsProtectionEnds() {
        DatasetEntry entry = entry(entries, "anchor+doom/protected/root.root-sentinel/ember.spark-dart");

        assertThat(entry.question()).isEqualTo("My opponent played Root Sentinel on their last turn; it has 3 defense"
                + " left. If I cast Spark Dart on it and neither player does anything else, what happens to it?");
        assertThat(entry.keyFacts()).containsExactly(
                "Root Sentinel is at 0 defense but anchored: it is doomed.",
                "Root Sentinel is no longer anchored.",
                "Root Sentinel is destroyed.");
        assertThat(entry.rules()).containsExactly("11.3.4", "5.2.1", "11.3.1", "5.4.3", "11.3.5");
        assertThat(entry.contextRules()).as("an event already carries 11.3.1").isEmpty();
    }

    @Test
    void aUnitWhoseProtectionIsOverDiesAtOnce() {
        DatasetEntry entry = entry(entries, "anchor+doom/expired/root.root-sentinel/ember.cinderfall");

        assertThat(entry.keyFacts()).containsExactly("Root Sentinel is destroyed.");
        assertThat(entry.rules()).containsExactly("6.6");
        assertThat(entry.contextRules()).containsExactly("11.3.1", "11.3.6");
    }
}
