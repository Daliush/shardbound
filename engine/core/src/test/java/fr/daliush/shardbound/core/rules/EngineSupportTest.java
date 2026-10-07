package fr.daliush.shardbound.core.rules;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.rules.play.EngineSupport;
import fr.daliush.shardbound.core.testing.TestContent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The cards the engine cannot play yet. The list shrinks with each slice of spec §17 and must be empty
 * once the keywords are in (slice 4).
 */
class EngineSupportTest {

    @Test
    void listsTheCardsThatAreNotPlayableYet() {
        List<String> unsupported = TestContent.catalog().all().stream()
                .filter(card -> !EngineSupport.supports(card))
                .map(CardDefinition::id)
                .map(Object::toString)
                .sorted()
                .toList();

        assertThat(unsupported).containsExactly(
                "neutral.binding-thread");
    }
}
