package fr.daliush.shardbound.corpus;

import static fr.daliush.shardbound.corpus.testing.CorpusTesting.CATALOG;
import static fr.daliush.shardbound.corpus.testing.CorpusTesting.CHUNKS;
import static fr.daliush.shardbound.corpus.testing.CorpusTesting.ROOT;
import static fr.daliush.shardbound.corpus.testing.CorpusTesting.findChunkById;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CorpusGeneratorTest {

    private static final Pattern RULE_ID =
            Pattern.compile("(?m)^\\s*(?:-|\\d+\\.)\\s+\\*\\*(\\d+\\.\\d+(?:\\.\\d+)?)\\*\\*");

    @Test
    void holdsOneChunkPerRuleTermAndCard() throws IOException {
        assertThat(CHUNKS).extracting(Chunk::id).doesNotHaveDuplicates();
        assertThat(Set.copyOf(listChunkIdsOfKind(Chunk.Kind.RULE))).isEqualTo(readRuleIdsFromRulebook()).hasSize(130);
        assertThat(listChunkIdsOfKind(Chunk.Kind.TERM)).hasSize(13)
                .contains("vocabulary.owner", "vocabulary.controller");
        assertThat(listChunkIdsOfKind(Chunk.Kind.CARD)).hasSameElementsAs(
                CATALOG.all().stream().map(card -> card.id().value()).toList());
    }

    @Test
    void cutsTheRulebookIntoRulesAndTerms() {
        assertThat(findChunkById("11.3.5").text()).isEqualTo("[11.3 Anchor] At the end of the turn during which its "
                + "protection ended, a doomed unit whose defense is still 0 is destroyed (5.4.3). Since \"Turn end\" "
                + "abilities resolve first (5.4.1), an end-of-turn heal can still save it.");
        assertThat(findChunkById("11.3.3").text()).endsWith("\n> *Example*: I sacrifice my anchored unit to pay for a "
                + "card. The sacrifice counts as paid, the card applies its effect, and the unit stays on the board.");
        assertThat(findChunkById("11.3.4").text()).contains("is **doomed**:");
        assertThat(findChunkById("vocabulary.owner").text())
                .isEqualTo("**Owner**: The player whose deck contained the card.");
        assertThat(findChunkById("vocabulary.owner").source()).isEqualTo("docs/rules/00-vocabulary.md");
    }

    @Test
    void expandsASectionIntoAllOfItsRules() {
        assertThat(findChunkById("11.3.5").refs()).containsExactly("5.4.3", "5.4.1");
        assertThat(findChunkById("5.2.1").refs())
                .containsExactly("11.3.1", "11.3.2", "11.3.3", "11.3.4", "11.3.5", "11.3.6");
        assertThat(findChunkById("root.root-sentinel").refs())
                .containsExactly("11.3.1", "11.3.2", "11.3.3", "11.3.4", "11.3.5", "11.3.6", "8.1");
    }

    /** A ref the rulebook mistypes, such as "(9.99)", stays as written and fails here. */
    @Test
    void refersOnlyToOtherChunks() {
        Set<String> chunkIds = CHUNKS.stream().map(Chunk::id).collect(Collectors.toSet());

        assertThat(CHUNKS).allSatisfy(chunk -> assertThat(chunk.refs())
                .doesNotContain(chunk.id())
                .doesNotHaveDuplicates()
                .allSatisfy(ref -> assertThat(chunkIds).contains(ref)));
    }

    @Test
    void namesTheFileEachChunkComesFrom() {
        assertThat(CHUNKS).allSatisfy(chunk -> assertThat(ROOT.resolve(chunk.source())).isRegularFile());
    }

    @Test
    void prefixesEveryRuleWithAtLeastOneSectionTitle() {
        assertThat(CHUNKS).filteredOn(chunk -> chunk.kind() == Chunk.Kind.RULE)
                .allSatisfy(chunk -> assertThat(chunk.text()).matches("(?s)\\[\\d[^]]*] .+"));
    }

    @Test
    void leavesTheRuleIdOutOfItsText() {
        assertThat(CHUNKS).allSatisfy(chunk -> assertThat(chunk.text()).doesNotContain("**" + chunk.id() + "**"));
    }

    private static List<String> listChunkIdsOfKind(Chunk.Kind kind) {
        return CHUNKS.stream().filter(chunk -> chunk.kind() == kind).map(Chunk::id).toList();
    }

    /** Every rule ID that starts an item of the rulebook, read without the chunker. */
    private static Set<String> readRuleIdsFromRulebook() throws IOException {
        try (Stream<Path> files = Files.list(ROOT.resolve("docs/rules"))) {
            return files.filter(file -> !Set.of("README.md", "12-open-points.md")
                            .contains(file.getFileName().toString()))
                    .map(CorpusGeneratorTest::readFile)
                    .flatMap(markdown -> RULE_ID.matcher(markdown).results().map(match -> match.group(1)))
                    .collect(Collectors.toSet());
        }
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
