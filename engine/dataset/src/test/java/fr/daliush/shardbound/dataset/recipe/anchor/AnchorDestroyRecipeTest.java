package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnchorDestroyRecipeTest {

    private final List<DatasetEntry> entries = entries(new AnchorDestroyRecipe());

    @Test
    void meetsEveryAnchorUnitWithEveryDestroySpellOnEachSideItCanHit() {
        // 2 Anchor units × (Crystal Rupture on the opponent's side + Tempest on both sides) × protected or expired.
        assertThat(entries).hasSize(12);
        assertThat(entries).extracting(DatasetEntry::id).contains(
                "anchor+destroy/opponents/protected/root.root-sentinel/neutral.crystal-rupture",
                "anchor+destroy/mine/expired/root.oakheart-guardian/neutral.tempest");
        assertThat(entries).extracting(DatasetEntry::id)
                .noneMatch(id -> id.startsWith("anchor+destroy/mine/") && id.endsWith("crystal-rupture"));
    }

    @Test
    void aProtectedUnitStaysOnTheBoard() {
        DatasetEntry entry = entry("anchor+destroy/opponents/protected/root.root-sentinel/neutral.crystal-rupture");

        assertThat(entry.question()).isEqualTo("My opponent played Root Sentinel on their last turn."
                + " If I cast Crystal Rupture on it, what happens to it?");
        assertThat(entry.keyFacts()).containsExactly("Root Sentinel is anchored: it is not destroyed.");
        assertThat(entry.rules()).containsExactly("11.3.2", "11.3.3");
        assertThat(entry.contextRules()).as("protected since it arrived").containsExactly("11.3.1");
        assertThat(entry.trace()).contains("CardPlayed[6.3]", "AnchorPrevented[11.3.2, 11.3.3]");
        assertThat(entry.cards()).containsExactly("neutral.crystal-rupture", "root.root-sentinel");
    }

    @Test
    void aUnitWhoseProtectionIsOverIsDestroyed() {
        DatasetEntry entry = entry("anchor+destroy/mine/expired/root.oakheart-guardian/neutral.tempest");

        assertThat(entry.question()).isEqualTo("My Oakheart Guardian has been on the board since my last turn."
                + " If I cast Tempest, what happens to it?");
        assertThat(entry.keyFacts()).containsExactly("Oakheart Guardian is destroyed.");
        assertThat(entry.rules()).containsExactly("8.2");
        assertThat(entry.contextRules()).as("no event says the protection is over")
                .containsExactly("11.3.1", "11.3.6");
    }

    private DatasetEntry entry(String id) {
        return entries.stream().filter(entry -> entry.id().equals(id)).findFirst().orElseThrow();
    }
}
