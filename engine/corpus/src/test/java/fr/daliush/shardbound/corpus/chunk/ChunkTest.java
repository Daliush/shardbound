package fr.daliush.shardbound.corpus.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkTest {

    @Test
    void hashesItsTextOnly() {
        Chunk chunk = new Chunk("1.1", Chunk.Kind.RULE, "[1. The game] Each player starts with 30 HP.", List.of(), "a");
        Chunk sameText = new Chunk("1.2", Chunk.Kind.RULE, chunk.text(), List.of("1.1"), "b");
        Chunk otherText = new Chunk("1.1", Chunk.Kind.RULE, "Other text.", List.of(), "a");

        assertThat(chunk.hash()).hasSize(64).isEqualTo(sameText.hash()).isNotEqualTo(otherText.hash());
    }

    @Test
    void keepsItsHashWhenItsRefsAreExpanded() {
        Chunk chunk = new Chunk("5.2.1", Chunk.Kind.RULE, "Anchor protections end (11.3).", List.of("11.3"), "a");

        assertThat(chunk.copyWithRefs(List.of("11.3.1")).hash()).isEqualTo(chunk.hash());
    }
}
