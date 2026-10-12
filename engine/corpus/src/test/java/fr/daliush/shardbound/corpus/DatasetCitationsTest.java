package fr.daliush.shardbound.corpus;

import static fr.daliush.shardbound.corpus.testing.CorpusTesting.CHUNKS;
import static fr.daliush.shardbound.corpus.testing.CorpusTesting.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The retrieval metrics count a question's {@code citations} as the chunks it needs, so a citation with no chunk
 * could never be retrieved and would cap the recall below 100% without a word.
 */
class DatasetCitationsTest {

    private static final Path DATASETS = ROOT.resolve("evals/datasets");
    private static final Set<String> FINAL_FILES = Set.of("dataset-dev.jsonl", "dataset-test.jsonl");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void findsAChunkForEveryCitationOfEveryCommittedDatasetVersion() throws IOException {
        Set<String> chunkIds = CHUNKS.stream().map(Chunk::id).collect(Collectors.toSet());
        List<Path> files = listFinalDatasetFiles();

        assertThat(files).isNotEmpty();
        assertThat(files).allSatisfy(file -> assertThat(readCitations(file)).isSubsetOf(chunkIds));
    }

    /** {@code preview} is a local dry run, not a committed version. */
    private static List<Path> listFinalDatasetFiles() throws IOException {
        try (Stream<Path> files = Files.walk(DATASETS, 2)) {
            return files.filter(file -> FINAL_FILES.contains(file.getFileName().toString()))
                    .filter(file -> !file.getParent().getFileName().toString().equals("preview"))
                    .sorted()
                    .toList();
        }
    }

    private static Set<String> readCitations(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.filter(line -> !line.isBlank())
                    .map(MAPPER::readTree)
                    .flatMap(question -> question.get("citations").valueStream())
                    .map(JsonNode::asString)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
