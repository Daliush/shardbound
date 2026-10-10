package fr.daliush.shardbound.dataset;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.RULEBOOK;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RulebookTest {

    @Test
    void readsARuleWithItsItemsButWithoutItsIdMarkdownOrExamples() {
        assertThat(RULEBOOK.text("7.5")).hasValue("Intercept: when an attack targets one of their units, the defender "
                + "may redirect the attack to another of their units, which becomes the new target. - A unit can "
                + "intercept at most once per turn. - A frozen unit cannot intercept. - Neither a spell nor an echo "
                + "can be intercepted (11.1.4).");
        assertThat(RULEBOOK.text("8.5").orElseThrow()).doesNotContain("Example").contains("Expiration alone");
        assertThat(RULEBOOK.text("5.2.1")).hasValue("Anchor protections on the active player's units end (11.3).");
        assertThat(RULEBOOK.has("8.99")).isFalse();
    }

    @Test
    void findsAWordAnywhereInTheRulebookIgnoringCase() {
        assertThat(RULEBOOK.mentions("fatigue")).isTrue();
        assertThat(RULEBOOK.mentions("Siphon")).isFalse();
    }

    @Test
    void knowsTheOpenPointsStillOpen() {
        assertThat(RULEBOOK.isStillOpen("Unit size")).isTrue();
        assertThat(RULEBOOK.isStillOpen("Fatigue")).isFalse();
    }
}
