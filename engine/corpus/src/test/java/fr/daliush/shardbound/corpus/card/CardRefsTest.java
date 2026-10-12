package fr.daliush.shardbound.corpus.card;

import static fr.daliush.shardbound.corpus.testing.CorpusTesting.findCardById;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CardRefsTest {

    @Test
    void refersToItsKeywordsSectionsThenToTheRulesOfItsTriggersAndEffects() {
        assertThat(findRefsOfCard("root.root-sentinel")).containsExactly("11.3", "8.1");
        assertThat(findRefsOfCard("ember.ash-warden")).containsExactly("11.1", "8.1", "8.5");
        assertThat(findRefsOfCard("neutral.binding-thread")).containsExactly("8.11", "11.5");
    }

    @Test
    void refersToARuleOncePerEffect() {
        assertThat(findRefsOfCard("tide.moonpull")).containsExactly("11.2", "8.5", "8.5", "8.10");
    }

    @Test
    void refersToTheCardOfATokenItSummons() {
        assertThat(findRefsOfCard("root.sproutcaller")).containsExactly("9.2", "8.1", "8.9", "root.sprout");
        assertThat(findRefsOfCard("root.heartwood-shrine")).containsExactly("9.5", "8.9", "root.sprout");
    }

    private static List<String> findRefsOfCard(String cardId) {
        return CardRefs.findRefsOfCard(findCardById(cardId));
    }
}
