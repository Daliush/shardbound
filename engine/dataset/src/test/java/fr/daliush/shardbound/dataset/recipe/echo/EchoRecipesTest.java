package fr.daliush.shardbound.dataset.recipe.echo;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class EchoRecipesTest {

    @Test
    void theEchoHitsTheAskersOnlyUnitOrTheAskerWhenTheyHaveNone() {
        List<DatasetEntry> entries = entries(new EchoDeathRecipe());
        // 2 units with one Echo attack × (Crystal Rupture, Tempest, Spark Dart, Cinderfall) × one unit or none.
        assertThat(entries).hasSize(16);

        assertThat(entry(entries, "echo+death/one-unit/ember.ashborn-drake/neutral.crystal-rupture").keyFacts())
                .containsExactly("Ashborn Drake is destroyed.", "Ashborn Drake died: Inferno Breath echoes at 200%.",
                        "Shardling takes 14 damage.", "Shardling is destroyed.");
        assertThat(entry(entries, "echo+death/no-unit/ember.ash-warden/ember.spark-dart").keyFacts())
                .as("4 damage at 50%").contains("You take 2 damage.");
        assertThat(entry(entries, "echo+death/one-unit/ember.ash-warden/neutral.tempest").keyFacts())
                .as("Tempest kills the asker's unit too").contains("Shardling is destroyed.", "You take 2 damage.");
    }

    @Test
    void aUnitReturnedToHandDoesNotEcho() {
        List<DatasetEntry> entries = entries(new EchoReturnToHandRecipe());

        // 3 Echo units, Kiln Wyrm's two Echo attacks included × (Receding Wave + Engulf).
        assertThat(entries).hasSize(6).allSatisfy(entry -> {
            assertThat(entry.keyFacts()).singleElement().asString().endsWith("returns to your opponent's hand.");
            assertThat(entry.contextRules()).containsExactly("11.1.5");
        });
    }

    @Test
    void anEchoThatKillsAnEchoUnitStartsAChain() {
        List<DatasetEntry> entries = entries(new EchoChainRecipe());
        assertThat(entries).hasSize(4);

        DatasetEntry chain = entry(entries,
                "echo+chain/ember.ash-warden/ember.ashborn-drake/neutral.crystal-rupture");
        assertThat(chain.keyFacts()).containsExactly("Ash Warden is destroyed.",
                "Ash Warden died: Cinder Bite echoes at 50%.", "Ashborn Drake takes 2 damage.",
                "Ashborn Drake is destroyed.", "Ashborn Drake died: Inferno Breath echoes at 200%.",
                "Your opponent takes 14 damage.");
        assertThat(chain.contextRules()).containsExactly("11.1.3", "11.1.6");
    }

    @Test
    void echoesOfUnitsThatDieTogetherResolveActivePlayerFirst() {
        DatasetEntry entry = entry(entries(new EchoChainRecipe()),
                "echo+chain/ember.ashborn-drake/ember.ash-warden/neutral.tempest");

        assertThat(entry.keyFacts()).containsSubsequence("Ash Warden died: Cinder Bite echoes at 50%.",
                "Your opponent takes 2 damage.", "Ashborn Drake died: Inferno Breath echoes at 200%.",
                "You take 14 damage.");
        assertThat(entry.contextRules()).containsExactly("11.1.3", "9.8");
    }

    @Test
    void anEchoReplaysThePrintedAttackWithoutItsBoost() {
        List<DatasetEntry> entries = entries(new EchoModifyRecipe());
        // 2 Echo units × (Roaring Pyre, Kindle, Swell), but Ash Warden does not boost itself with its own Kindle.
        assertThat(entries).hasSize(5);

        DatasetEntry entry = entry(entries, "echo+modify/ember.ash-warden/ember.roaring-pyre/neutral.tempest");
        assertThat(entry.keyFacts()).contains("Ash Warden gets +3/+0 until end of turn.",
                "Your opponent takes 2 damage.");
        assertThat(entry(entries, "echo+modify/ember.ash-warden/tide.reefkeeper:swell/neutral.tempest").question())
                .startsWith("I have Ash Warden and Reefkeeper on my board");
    }

    @Test
    void theOrderOfTwoEchoesDecidesWhatTheSecondOneHits() {
        List<DatasetEntry> entries = entries(new EchoOrderRecipe());

        assertThat(entry(entries, "echo+order/ember.kiln-wyrm/first-0/neutral.crystal-rupture").keyFacts())
                .as("Scorch, then Kiln Blast: both hit the Shardling").endsWith("Shardling is destroyed.");
        DatasetEntry reversed = entry(entries, "echo+order/ember.kiln-wyrm/first-1/neutral.crystal-rupture");
        assertThat(reversed.keyFacts()).as("Kiln Blast kills the Shardling, so Scorch hits the player")
                .containsSubsequence("Shardling is destroyed.", "Kiln Wyrm died: Scorch echoes at 100%.",
                        "Your opponent takes 2 damage.");
        assertThat(reversed.contextRules()).contains("11.1.8");
    }
}
