package fr.daliush.shardbound.dataset.recipe.overcharge;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverchargeRecipesTest {

    private final List<DatasetEntry> locks = entries(new OverchargeLockRecipe());

    @Test
    void anOverchargedCardLocksTwoShardsOnTheNextTurn() {
        // Each Overcharge card alone, both together, and both with too few Shards.
        assertThat(locks).hasSize(4);

        assertThat(entry(locks, "overcharge+locked-shards/one/ember.ember-lance").keyFacts()).containsExactly(
                "You play Ember Lance overcharged (2 Shards).",
                "Your next turn will have 2 Shards more locked (2 in all).",
                "You have 4 Shards (max 6, 2 locked).");
    }

    @Test
    void locksStackAndTheirExcessIsLost() {
        assertThat(entry(locks, "overcharge+locked-shards/stacked").keyFacts())
                .contains("You have 2 Shards (max 6, 4 locked).");
        assertThat(entry(locks, "overcharge+locked-shards/excess").keyFacts())
                .containsSubsequence("You have 0 Shards (max 3, 4 locked).", "You have 4 Shards (max 4).");
    }

    @Test
    void overchargeNeverLowersTheCostOfAnAttack() {
        List<DatasetEntry> entries = entries(new OverchargeAttackCostRecipe());

        assertThat(entries).extracting(DatasetEntry::keyFacts).containsExactly(
                List.of("You cannot attack with Blaze Mastiff's Maul."),
                List.of("You can attack with Blaze Mastiff's Maul."));
        assertThat(entries).allSatisfy(entry -> assertThat(entry.contextRules()).contains("11.4.5"));
    }
}
