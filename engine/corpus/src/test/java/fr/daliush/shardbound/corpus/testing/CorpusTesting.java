package fr.daliush.shardbound.corpus.testing;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.corpus.CorpusGenerator;
import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.nio.file.Path;
import java.util.List;

/** The repository's real content and chunks, found by walking up from the working directory. */
public final class CorpusTesting {

    public static final Path ROOT = ContentLoader.find(Path.of("")).orElseThrow().getParent();
    public static final Content CONTENT = ContentLoader.load(ROOT.resolve("content"));
    public static final CardCatalog CATALOG = CONTENT.catalog();
    public static final List<Chunk> CHUNKS = CorpusGenerator.buildAllChunks(ROOT, CONTENT);

    private CorpusTesting() {
    }

    public static CardDefinition findCardById(String id) {
        return CATALOG.card(new CardId(id));
    }

    public static Chunk findChunkById(String id) {
        return CHUNKS.stream().filter(chunk -> chunk.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("No chunk " + id));
    }
}
