package fr.daliush.shardbound.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.dataset.card.CardQuestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetWriterTest {

    @Test
    void writesOneEntryPerLineWithSnakeCaseKeys() {
        DatasetEntry entry = new DatasetEntry("anchor+destroy/x", "interaction", "anchor+destroy", "What happens?",
                List.of("root.root-sentinel"), List.of("Root Sentinel is destroyed."), List.of("8.2"),
                List.of("11.3.6"), List.of("UnitDestroyed[8.2]"));

        String lines = new DatasetWriter().toJsonLines(List.of(entry, entry));

        assertThat(lines.lines()).hasSize(2).first().isEqualTo("""
                {"id":"anchor+destroy/x","category":"interaction","interaction":"anchor+destroy",\
                "question":"What happens?","cards":["root.root-sentinel"],\
                "key_facts":["Root Sentinel is destroyed."],"rules":["8.2"],"context_rules":["11.3.6"],\
                "trace":["UnitDestroyed[8.2]"]}""");
    }

    @Test
    void writesAnyRecordTheSameWay() {
        CardQuestion question = new CardQuestion("card/neutral.shardling/cost", "factual", "neutral.shardling",
                "How much does Shardling cost?", List.of("Shardling costs 1 Shard."));

        assertThat(new DatasetWriter().toJsonLines(List.of(question))).isEqualTo("""
                {"id":"card/neutral.shardling/cost","category":"factual","card":"neutral.shardling",\
                "question":"How much does Shardling cost?","key_facts":["Shardling costs 1 Shard."]}
                """);
    }
}
