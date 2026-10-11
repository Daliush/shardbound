package fr.daliush.shardbound.corpus.rulebook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WrittenRefsTest {

    @Test
    void findsRuleAndSectionNumbersInTheOrderTheTextCitesThem() {
        String doom = "Destroyed (5.4.3), following rule 9.8 (see Fracture, 11.2.5), or 11.1.10.";
        String shards = "Shards refill (4.1, 4.2). Anchor ends (11.3). Attack with a unit (7).";

        assertThat(WrittenRefs.findRefsInText(doom)).containsExactly("5.4.3", "9.8", "11.2.5", "11.1.10");
        assertThat(WrittenRefs.findRefsInText(shards)).containsExactly("4.1", "4.2", "11.3", "7");
    }

    @Test
    void ignoresOtherNumbers() {
        String noRef = "Deal 3 damage. Echo 50 halves it. A Fracture N card has N steps (from 2 to 5).";

        assertThat(WrittenRefs.findRefsInText(noRef)).isEmpty();
    }
}
