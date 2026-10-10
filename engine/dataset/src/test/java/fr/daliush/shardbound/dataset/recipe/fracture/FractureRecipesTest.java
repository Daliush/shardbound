package fr.daliush.shardbound.dataset.recipe.fracture;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entries;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.entry;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.DatasetEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class FractureRecipesTest {

    private final List<DatasetEntry> steps = entries(new FractureStepsRecipe());

    @Test
    void eachStepReturnsToHandUntilTheLast() {
        // Moonpull and Spring Tide: 3 steps + 2 legality questions each; Ebb and Surge: 2 steps + 2.
        assertThat(steps).hasSize(14);

        assertThat(entry(steps, "fracture+steps/step-1/tide.moonpull").keyFacts())
                .containsExactly("Shardling gets +2/+2.", "Moonpull returns to your hand, step 2.");
        DatasetEntry last = entry(steps, "fracture+steps/step-3/tide.spring-tide");
        assertThat(last.keyFacts()).contains("Spring Tide goes to your graveyard.");
        assertThat(last.rules()).contains("11.2.4");
    }

    @Test
    void aStepThatReturnsAUnitSaysSo() {
        assertThat(entry(steps, "fracture+steps/step-2/tide.ebb-and-surge").keyFacts())
                .containsExactly("Shard Construct returns to your opponent's hand.",
                        "Ebb and Surge goes to your graveyard.");
    }

    @Test
    void theAskerDoesNotSeeTheCardTheirOpponentDraws() {
        assertThat(entry(steps, "fracture+steps/step-1/tide.spring-tide").keyFacts())
                .contains("Your opponent draws a card.");
    }

    @Test
    void twoStepsCannotBePlayedInTheSameTurn() {
        DatasetEntry thisTurn = entry(steps, "fracture+steps/next-step-this-turn/tide.moonpull");
        DatasetEntry nextTurn = entry(steps, "fracture+steps/next-step-next-turn/tide.moonpull");

        assertThat(thisTurn.keyFacts()).containsExactly("You cannot play the next step of Moonpull this turn.");
        assertThat(nextTurn.keyFacts()).containsExactly("You can play the next step of Moonpull on your next turn.");
        assertThat(thisTurn.rules()).as("no event says it").isEmpty();
        assertThat(thisTurn.contextRules()).containsExactly("11.2.3");
    }

    @Test
    void aFractureCardLosesItsProgressInAFullHandOrWhenDiscarded() {
        List<DatasetEntry> entries = entries(new FractureLostProgressRecipe());
        // Spring Tide's step 2 draws into a full hand; each of the 3 Fracture cards is discarded by Brinesong.
        assertThat(entries).hasSize(4);

        assertThat(entry(entries, "fracture+lost-progress/full-hand/step-2/tide.spring-tide").keyFacts())
                .contains("Your hand is full: Spring Tide goes to the graveyard.");
        DatasetEntry discarded = entry(entries, "fracture+lost-progress/discarded/tide.moonpull/tide.brinesong");
        assertThat(discarded.keyFacts()).containsExactly("You discard Moonpull.");
        assertThat(discarded.rules()).contains("11.2.6");
    }
}
