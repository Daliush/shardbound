package fr.daliush.shardbound.corpus.rulebook;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** The rulebook's chunks: one per vocabulary term, then one per rule, file after file. */
public final class RulebookChunker {

    private static final String RULES_DIR = "docs/rules";
    private static final String VOCABULARY_FILE = "00-vocabulary.md";
    /** The vocabulary has its own chunker; the README and the open points describe the rulebook, not rules. */
    private static final Set<String> NOT_RULE_FILES = Set.of(VOCABULARY_FILE, "README.md", "12-open-points.md");

    private RulebookChunker() {
    }

    /** {@code root} is the repository, holding {@code docs/rules/}. */
    public static List<Chunk> chunkRulebook(Path root) {
        List<Chunk> chunks = new ArrayList<>(chunkVocabularyFile(root));
        listRuleFiles(root).forEach(file -> chunks.addAll(chunkRuleFile(file)));
        return chunks;
    }

    private static List<Chunk> chunkVocabularyFile(Path root) {
        Path file = root.resolve(RULES_DIR).resolve(VOCABULARY_FILE);
        return VocabularyChunker.chunkTerms(readFile(file), RULES_DIR + "/" + VOCABULARY_FILE);
    }

    private static List<Chunk> chunkRuleFile(Path file) {
        return RuleChunker.chunkRules(readFile(file), RULES_DIR + "/" + file.getFileName());
    }

    private static List<Path> listRuleFiles(Path root) {
        try (Stream<Path> files = Files.list(root.resolve(RULES_DIR))) {
            return files.filter(file -> file.toString().endsWith(".md"))
                    .filter(file -> !NOT_RULE_FILES.contains(file.getFileName().toString()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
