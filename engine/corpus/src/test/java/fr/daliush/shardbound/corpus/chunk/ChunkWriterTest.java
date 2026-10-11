package fr.daliush.shardbound.corpus.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkWriterTest {

    @Test
    void writesOneJsonLinePerChunkWithALowercaseKind() {
        Chunk rule = new Chunk("11.3.5", Chunk.Kind.RULE, "[11.3 Anchor] It is destroyed (5.4.3).", List.of("5.4.3"),
                "docs/rules/11-3-anchor.md", "abc");
        Chunk card = new Chunk("neutral.tempest", Chunk.Kind.CARD, "Tempest: neutral spell.", List.of("8.2"),
                "content/cards/neutral/tempest.json", "def");

        assertThat(new ChunkWriter().toJsonLines(List.of(rule, card))).isEqualTo("""
                {"id":"11.3.5","kind":"rule","text":"[11.3 Anchor] It is destroyed (5.4.3).","refs":["5.4.3"],\
                "source":"docs/rules/11-3-anchor.md","hash":"abc"}
                {"id":"neutral.tempest","kind":"card","text":"Tempest: neutral spell.","refs":["8.2"],\
                "source":"content/cards/neutral/tempest.json","hash":"def"}
                """);
    }
}
