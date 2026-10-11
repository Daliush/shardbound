package fr.daliush.shardbound.corpus.rulebook;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleChunkerTest {

    private static final String SECTION = """
            # 5. Game sequence

            *Part of the [Shardbound rulebook](README.md).*

            ## 5.2 Start of turn (in this order)

            1. **5.2.1** Anchor protections on the active player's units end (11.3).
            2. **5.2.2** The active player gains and refills their Shards (4.1, 4.2).

            ## 5.4 End of turn (in this order)

            - **5.4.2** **Modify +X/+Y**: lasts until end of turn;
              - unless it is **permanent**.

              > *Example*: a unit gets +2 until end of turn.
            - **5.4.3** Doomed units are destroyed (11.3.4), before 5.4.4.

            > *Example*: an example outside a rule belongs to none.
            """;

    private final List<Chunk> chunks = RuleChunker.chunkRules(SECTION, "docs/rules/05-game-sequence.md");

    @Test
    void cutsOneChunkPerRule() {
        assertThat(chunks).extracting(Chunk::id).containsExactly("5.2.1", "5.2.2", "5.4.2", "5.4.3");
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.kind()).isEqualTo(Chunk.Kind.RULE);
            assertThat(chunk.source()).isEqualTo("docs/rules/05-game-sequence.md");
        });
    }

    @Test
    void prefixesEachRuleWithTheTitlesOfItsSectionsButNotItsId() {
        assertThat(chunks.getFirst().text()).isEqualTo(
                "[5. Game sequence > 5.2 Start of turn (in this order)] Anchor protections on the active player's "
                        + "units end (11.3).");
        assertThat(chunks.get(3).text()).startsWith("[5. Game sequence > 5.4 End of turn (in this order)] Doomed");
    }

    @Test
    void keepsItsItemsAndExamplesInMarkdownButNotAnExampleOutsideIt() {
        assertThat(chunks.get(2).text()).isEqualTo("""
                [5. Game sequence > 5.4 End of turn (in this order)] **Modify +X/+Y**: lasts until end of turn;
                - unless it is **permanent**.
                > *Example*: a unit gets +2 until end of turn.""");
        assertThat(chunks).noneSatisfy(chunk -> assertThat(chunk.text()).contains("outside a rule"));
    }

    @Test
    void keepsTheRefsItsTextWrites() {
        assertThat(chunks).extracting(Chunk::refs).containsExactly(
                List.of("11.3"), List.of("4.1", "4.2"), List.of(), List.of("11.3.4", "5.4.4"));
    }
}
