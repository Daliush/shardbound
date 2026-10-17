package fr.daliush.shardbound.corpus.rulebook;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class VocabularyChunkerTest {

    private static final String VOCABULARY = """
            # 0. Vocabulary

            | Term | Meaning |
            |---|---|
            | **HP** | A **player's** hit points. |
            | **Attack ability** | What a card does, in the form *trigger → effect → target*. |
            """;

    @Test
    void cutsOneChunkPerTermInMarkdownWithoutAHeading() {
        List<Chunk> chunks = VocabularyChunker.chunkTerms(VOCABULARY, "docs/rules/00-vocabulary.md");

        assertThat(chunks).extracting(Chunk::id).containsExactly("vocabulary.hp", "vocabulary.attack-ability");
        assertThat(chunks).extracting(Chunk::text).containsExactly("**HP**: A **player's** hit points.",
                "**Attack ability**: What a card does, in the form *trigger → effect → target*.");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.kind()).isEqualTo(Chunk.Kind.TERM));
    }

    @Test
    void namesEachChunkAfterItsTerm() {
        assertThat(VocabularyChunker.chunkTerms(VOCABULARY, "docs/rules/00-vocabulary.md")).extracting(Chunk::name)
                .containsExactly("HP", "Attack ability");
    }
}
