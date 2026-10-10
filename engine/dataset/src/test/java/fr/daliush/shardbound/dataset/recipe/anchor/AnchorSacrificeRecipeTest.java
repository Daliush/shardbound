package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnchorSacrificeRecipeTest {

    private final List<DatasetEntry> entries = entries(new AnchorSacrificeRecipe());

    @Test
    void meetsEveryAnchorUnitWithEveryCardThatHasASacrificeCost() {
        // 3 Anchor units × (Pyre Offering + Flamebound Zealot) × protected or expired.
        assertThat(entries).hasSize(12);
    }

    @Test
    void aProtectedUnitStaysAndTheCardResolves() {
        DatasetEntry entry = entry(entries, "anchor+sacrifice/protected/root.root-sentinel/ember.pyre-offering");

        assertThat(entry.question()).isEqualTo("I played Root Sentinel this turn."
                + " If I sacrifice it to play Pyre Offering on my opponent's Shardling, what happens?");
        assertThat(entry.keyFacts()).containsExactly(
                "Root Sentinel is anchored: it stays on the board, and the sacrifice counts as paid.",
                "Shardling takes 8 damage.",
                "Shardling is destroyed.");
        assertThat(entry.rules()).startsWith("11.3.2", "11.3.3");
    }

    @Test
    void aUnitWhoseProtectionIsOverIsSacrificed() {
        DatasetEntry entry = entry(entries, "anchor+sacrifice/expired/root.root-sentinel/ember.flamebound-zealot");

        assertThat(entry.keyFacts()).containsExactly("Root Sentinel is sacrificed.",
                "Flamebound Zealot arrives on your board.");
        assertThat(entry.rules()).contains("8.3");
        assertThat(entry.contextRules()).containsExactly("11.3.1", "11.3.6");
    }
}
