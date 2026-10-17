package fr.daliush.shardbound.corpus.ref;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class SectionRefsTest {

    private static final Chunk ANCHOR_1 = ruleChunk("11.3.1", List.of());
    private static final Chunk ANCHOR_2 = ruleChunk("11.3.2", List.of());

    @Test
    void turnsASectionIntoAllOfItsRules() {
        Chunk turnStart = ruleChunk("5.2.1", List.of("11.3"));

        assertThat(expandedRefsOf(turnStart)).containsExactly("11.3.1", "11.3.2");
    }

    @Test
    void keepsARuleOrACardAsWritten() {
        Chunk doom = ruleChunk("11.3.5", List.of("5.4.3", "root.sprout"));

        assertThat(expandedRefsOf(doom)).containsExactly("5.4.3", "root.sprout");
    }

    @Test
    void dropsTheChunksOwnIdAndDuplicates() {
        Chunk protection = ruleChunk("11.3.2", List.of("11.3", "11.3.1", "11.3.1"));

        assertThat(SectionRefs.expandSectionRefs(List.of(ANCHOR_1, protection)).getLast().refs())
                .containsExactly("11.3.1");
    }

    private static List<String> expandedRefsOf(Chunk chunk) {
        return SectionRefs.expandSectionRefs(List.of(ANCHOR_1, ANCHOR_2, chunk)).getLast().refs();
    }

    private static Chunk ruleChunk(String id, List<String> refs) {
        return Chunk.rule(id, "Rule " + id + ".", refs, "r");
    }
}
