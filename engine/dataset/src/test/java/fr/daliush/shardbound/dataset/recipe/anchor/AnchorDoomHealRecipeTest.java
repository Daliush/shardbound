package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnchorDoomHealRecipeTest {

    private final List<DatasetEntry> entries = entries(new AnchorDoomHealRecipe());

    @Test
    void givesEachAnchorUnitOneScenarioWithoutAHealAndOnePerHealOfItsSide() {
        // 2 Anchor units × (no heal + Mossmender's Mend).
        assertThat(entries).hasSize(4);
    }

    @Test
    void aHealedUnitIsNoLongerDoomed() {
        DatasetEntry entry = entry(entries, "anchor+doom-heal/healed/root.root-sentinel/root.mossmender:mend");

        assertThat(entry.keyFacts()).as("Mend also heals Mossmender, which the question is not about")
                .containsExactly("Root Sentinel heals 2 defense.", "Root Sentinel is no longer doomed.");
        assertThat(entry.rules()).containsExactly("8.4", "11.3.4");
        assertThat(entry.contextRules()).as("why the end of the turn matters").containsExactly("11.3.5");
    }

    @Test
    void aUnitNotHealedIsDestroyedAtTheEndOfTheTurn() {
        DatasetEntry entry = entry(entries, "anchor+doom-heal/not-healed/root.oakheart-guardian");

        assertThat(entry.keyFacts()).containsExactly("Oakheart Guardian is destroyed.");
        assertThat(entry.rules()).containsExactly("5.4.3", "11.3.5");
    }
}
