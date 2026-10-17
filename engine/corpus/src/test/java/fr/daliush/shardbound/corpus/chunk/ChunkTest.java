package fr.daliush.shardbound.corpus.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkTest {

    @Test
    void hashesItsTextOnly() {
        Chunk chunk = Chunk.rule("1.1", "[1. The game] Each player starts with 30 HP.", List.of(), "a");
        Chunk sameText = Chunk.term("vocabulary.hp", "HP", chunk.text(), List.of("1.1"), "b");
        Chunk otherText = Chunk.rule("1.1", "Other text.", List.of(), "a");

        assertThat(chunk.hash()).hasSize(64).isEqualTo(sameText.hash()).isNotEqualTo(otherText.hash());
    }

    @Test
    void keepsItsNameAndHashWhenItsRefsAreExpanded() {
        Chunk chunk = Chunk.card("root.root-sentinel", "Root Sentinel", "Root Sentinel: Anchor.", List.of("11.3"), "a");
        Chunk expanded = chunk.copyWithRefs(List.of("11.3.1"));

        assertThat(expanded.hash()).isEqualTo(chunk.hash());
        assertThat(expanded.name()).isEqualTo("Root Sentinel");
    }

    @Test
    void refusesANamedRuleAndATermOrACardWithoutAName() {
        assertThatThrownBy(() -> new Chunk("7.6", Chunk.Kind.RULE, "No retaliation", "Text.", List.of(), "a", "h"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("7.6");
        assertThatThrownBy(() -> Chunk.term("vocabulary.hp", null, "Text.", List.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Chunk.card("neutral.tempest", null, "Text.", List.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
