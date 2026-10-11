package fr.daliush.shardbound.corpus;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.corpus.card.CardChunker;
import fr.daliush.shardbound.corpus.chunk.Chunk;
import fr.daliush.shardbound.corpus.chunk.ChunkWriter;
import fr.daliush.shardbound.corpus.ref.SectionRefs;
import fr.daliush.shardbound.corpus.rulebook.RulebookChunker;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Arbiter's chunks into {@code ai/.corpus/chunks.jsonl}, for the Python index (design doc §5.2). The file
 * is derived from the rulebook and the cards, so it is rewritten before each indexing and not committed. From
 * {@code engine/}: {@code ./mvnw -q -pl corpus -am compile exec:java}.
 */
public final class CorpusGenerator {

    private static final String OUTPUT_FILE = "ai/.corpus/chunks.jsonl";

    private CorpusGenerator() {
    }

    public static void main(String[] args) {
        Path contentDir = ContentLoader.find(Path.of("")).orElseThrow(
                () -> new IllegalStateException("No content/ folder above " + Path.of("").toAbsolutePath()));
        Path root = contentDir.getParent();
        List<Chunk> chunks = buildAllChunks(root, ContentLoader.load(contentDir));
        Path file = root.resolve(OUTPUT_FILE);
        new ChunkWriter().writeJsonLines(file, chunks);
        System.out.println(countChunksOfKind(chunks, Chunk.Kind.RULE) + " rules, "
                + countChunksOfKind(chunks, Chunk.Kind.TERM) + " terms and "
                + countChunksOfKind(chunks, Chunk.Kind.CARD) + " cards written to " + file);
    }

    /**
     * First pass: the chunks of the rulebook and of the cards, with their refs as written. Second pass, once every
     * rule is known: each section ref becomes the rules of its section.
     */
    public static List<Chunk> buildAllChunks(Path root, Content content) {
        List<Chunk> chunks = new ArrayList<>();
        chunks.addAll(RulebookChunker.chunkRulebook(root));
        chunks.addAll(CardChunker.chunkAllCards(content));
        return SectionRefs.expandSectionRefs(chunks);
    }

    private static long countChunksOfKind(List<Chunk> chunks, Chunk.Kind kind) {
        return chunks.stream().filter(chunk -> chunk.kind() == kind).count();
    }
}
