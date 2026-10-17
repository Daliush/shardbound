package fr.daliush.shardbound.corpus.card;

import static fr.daliush.shardbound.corpus.testing.CorpusTesting.CATALOG;
import static fr.daliush.shardbound.corpus.testing.CorpusTesting.CONTENT;
import static fr.daliush.shardbound.corpus.testing.CorpusTesting.findCardById;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.text.CardTextRenderer;
import fr.daliush.shardbound.corpus.chunk.Chunk;
import org.junit.jupiter.api.Test;

class CardChunkerTest {

    private static final CardTextRenderer RENDERER = new CardTextRenderer(CATALOG, CONTENT.textTemplates());

    @Test
    void writesWhatTheCardIsThenItsTextWithoutAHeading() {
        Chunk sentinel = chunkCardById("root.root-sentinel");

        assertThat(sentinel.id()).isEqualTo("root.root-sentinel");
        assertThat(sentinel.kind()).isEqualTo(Chunk.Kind.CARD);
        assertThat(sentinel.text()).isEqualTo("""
                Root Sentinel: Root unit, costs 2 Shards, 5 defense.
                Anchor.
                Root Slam (2 Shards): Deal 4 damage to the target.""");
        assertThat(sentinel.source()).isEqualTo("content/cards/root/root-sentinel.json");
    }

    @Test
    void namesTheChunkAfterItsCard() {
        assertThat(chunkCardById("root.root-sentinel").name()).isEqualTo("Root Sentinel");
    }

    @Test
    void writesNoCostForATokenOrAFractureSpell() {
        assertThat(chunkCardById("root.sprout").text()).startsWith("Sprout: Root token unit, 2 defense.\n");
        assertThat(chunkCardById("tide.moonpull").text()).startsWith("Moonpull: Tide spell.\nFracture 3.\n");
        assertThat(chunkCardById("neutral.tempest").text()).startsWith("Tempest: Neutral spell, costs");
        assertThat(chunkCardById("root.heartwood-shrine").text())
                .startsWith("Heartwood Shrine: Root relic, costs 4 Shards.");
    }

    @Test
    void chunksEveryCardOfTheCatalogSortedById() {
        assertThat(CardChunker.chunkAllCards(CONTENT)).extracting(Chunk::id)
                .hasSize(CATALOG.all().size())
                .isSorted();
    }

    private static Chunk chunkCardById(String id) {
        return CardChunker.chunkCard(findCardById(id), RENDERER);
    }
}
