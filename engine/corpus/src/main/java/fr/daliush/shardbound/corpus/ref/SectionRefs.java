package fr.daliush.shardbound.corpus.ref;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.util.List;

/**
 * The second pass over the refs, once every chunk is built. The chunkers keep the refs as written, and some are
 * section numbers: "(11.3)" stands for 11.3.1 to 11.3.6. A chunker reads one file at a time, so only now are the
 * rules of every section known.
 */
public final class SectionRefs {

    private SectionRefs() {
    }

    public static List<Chunk> expandSectionRefs(List<Chunk> chunks) {
        List<String> ruleIds = chunks.stream().filter(chunk -> chunk.kind() == Chunk.Kind.RULE).map(Chunk::id).toList();
        return chunks.stream().map(chunk -> expandSectionRefsOf(chunk, ruleIds)).toList();
    }

    /** A chunk never refers to itself, nor twice to the same chunk. */
    private static Chunk expandSectionRefsOf(Chunk chunk, List<String> ruleIds) {
        List<String> refs = chunk.refs().stream()
                .flatMap(ref -> expandIfSection(ref, ruleIds).stream())
                .filter(ref -> !ref.equals(chunk.id()))
                .distinct()
                .toList();
        return chunk.copyWithRefs(refs);
    }

    /** "11.3" gives 11.3.1 to 11.3.6. A rule or a card, such as "5.4.3" or "root.sprout", stays as written. */
    private static List<String> expandIfSection(String ref, List<String> ruleIds) {
        List<String> rulesOfSection = ruleIds.stream().filter(ruleId -> ruleId.startsWith(ref + ".")).toList();
        return rulesOfSection.isEmpty() ? List.of(ref) : rulesOfSection;
    }
}
