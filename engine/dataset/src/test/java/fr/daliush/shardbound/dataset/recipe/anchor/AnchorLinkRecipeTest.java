package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnchorLinkRecipeTest {

    private final List<DatasetEntry> entries = entries(new AnchorLinkRecipe());

    @Test
    void aimsAChosenTargetAtEachLinkedUnit() {
        // 3 Anchor units × (Crystal Rupture on each unit + Tempest) × protected or expired.
        assertThat(entries).hasSize(18);
    }

    @Test
    void tempestDestroysThePartnerOnlyAndBreaksTheLink() {
        DatasetEntry entry = entry(entries, "anchor+link/protected/aimed-at-all/root.root-sentinel/neutral.tempest");

        assertThat(entry.question()).isEqualTo("My opponent played Root Sentinel on their last turn, and it is linked"
                + " to their Shardling. If I cast Tempest, what happens?");
        assertThat(entry.keyFacts()).containsExactly(
                "Root Sentinel is anchored: it is not destroyed.",
                "Shardling is destroyed.",
                "Shardling left the board: its link with Root Sentinel breaks.");
        assertThat(entry.contextRules()).as("destruction is not shared").containsExactly("11.3.1", "11.5.3");
    }

    @Test
    void aProtectedUnitKeepsItsLink() {
        DatasetEntry entry = entry(entries,
                "anchor+link/protected/aimed-at-anchor/root.root-sentinel/neutral.crystal-rupture");

        assertThat(entry.keyFacts()).containsExactly("Root Sentinel is anchored: it is not destroyed.");
    }
}
