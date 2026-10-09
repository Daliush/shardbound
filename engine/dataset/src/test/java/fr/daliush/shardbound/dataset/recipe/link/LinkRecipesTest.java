package fr.daliush.shardbound.dataset.recipe.link;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkRecipesTest {

    @Test
    void theUnitHitTakesHalfRoundedUpAndItsPartnerTheRest() {
        List<DatasetEntry> entries = entries(new LinkDamageRecipe());
        // (Spark Dart, Cinderfall) × the partner on either side.
        assertThat(entries).hasSize(4);

        DatasetEntry entry = entry(entries, "link+damage/partner-mine/ember.spark-dart");
        assertThat(entry.keyFacts()).containsExactly("Shard Construct takes 2 damage.",
                "Shardling takes 1 damage through its link with Shard Construct.");
        assertThat(entry.contextRules()).as("a link across the board").containsExactly("8.11");
    }

    @Test
    void twoLinkedUnitsHitTogetherEachShareTheirDamage() {
        DatasetEntry entry = entry(entries(new LinkDamageRecipe()), "link+damage/partner-opponents/ember.cinderfall");

        assertThat(entry.keyFacts()).contains("Shard Construct takes 2 damage through its link with Shardling.",
                "Shardling is destroyed.", "Shardling left the board: its link with Shard Construct breaks.");
    }

    @Test
    void onlyDamageIsShared() {
        List<DatasetEntry> entries = entries(new LinkNoShareRecipe());
        // (Crystal Rupture, Rime Snare, Receding Wave) × the partner on either side.
        assertThat(entries).hasSize(6).allSatisfy(entry -> assertThat(entry.contextRules()).contains("11.5.3"));

        DatasetEntry frozen = entry(entries, "link+no-share/partner-mine/tide.rime-snare");
        assertThat(frozen.keyFacts()).containsExactly("Shard Construct is frozen until the end of turn 4.");
        assertThat(frozen.question()).as("the fact names a turn").startsWith("It is turn 3, my turn.");
    }
}
