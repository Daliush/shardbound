package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnchorRemovalRecipeTest {

    private final List<DatasetEntry> destroyed = entries(AnchorRemovalRecipe.destroy());
    private final List<DatasetEntry> returned = entries(AnchorRemovalRecipe.returnToHand());

    @Test
    void meetsEveryAnchorUnitWithEverySourceOnEachSideItCanHit() {
        // 3 Anchor units × (Crystal Rupture on the opponent's side + Tempest on both sides) × protected or expired.
        assertThat(destroyed).hasSize(18);
        assertThat(destroyed).extracting(DatasetEntry::id)
                .noneMatch(id -> id.startsWith("anchor+destroy/mine/") && id.endsWith("crystal-rupture"));
        // 3 Anchor units × (Receding Wave + Deepcurrent Behemoth's Engulf) × protected or expired.
        assertThat(returned).hasSize(12);
    }

    @Test
    void aProtectedUnitIsNotDestroyed() {
        DatasetEntry entry = entry(destroyed,
                "anchor+destroy/opponents/protected/root.root-sentinel/neutral.crystal-rupture");

        assertThat(entry.question()).isEqualTo("My opponent played Root Sentinel on their last turn."
                + " If I cast Crystal Rupture on it, what happens to it?");
        assertThat(entry.keyFacts()).containsExactly("Root Sentinel is anchored: it is not destroyed.");
        assertThat(entry.rules()).containsExactly("11.3.2", "11.3.3");
        assertThat(entry.contextRules()).as("protected since it arrived").containsExactly("11.3.1");
        assertThat(entry.cards()).containsExactly("neutral.crystal-rupture", "root.root-sentinel");
    }

    @Test
    void aUnitWhoseProtectionIsOverIsDestroyed() {
        DatasetEntry entry = entry(destroyed, "anchor+destroy/mine/expired/root.oakheart-guardian/neutral.tempest");

        assertThat(entry.question()).isEqualTo("My Oakheart Guardian has been on the board since my last turn."
                + " If I cast Tempest, what happens to it?");
        assertThat(entry.keyFacts()).containsExactly("Oakheart Guardian is destroyed.");
        assertThat(entry.rules()).containsExactly("8.2");
        assertThat(entry.contextRules()).as("no event says the protection is over")
                .containsExactly("11.3.1", "11.3.6");
    }

    @Test
    void anAttackCannotReturnAProtectedUnitToHand() {
        DatasetEntry entry = entry(returned,
                "anchor+return-to-hand/opponents/protected/root.root-sentinel/tide.deepcurrent-behemoth:engulf");

        assertThat(entry.question()).endsWith("If I attack it with Deepcurrent Behemoth's Engulf, what happens to it?");
        assertThat(entry.keyFacts()).containsExactly("Root Sentinel is anchored: it does not return to hand.");
    }

    @Test
    void aUnitWhoseProtectionIsOverReturnsToHand() {
        DatasetEntry entry = entry(returned,
                "anchor+return-to-hand/opponents/expired/root.root-sentinel/tide.receding-wave");

        assertThat(entry.keyFacts()).containsExactly("Root Sentinel returns to your opponent's hand.");
        assertThat(entry.rules()).containsExactly("8.8", "6.7");
    }
}
