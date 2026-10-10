package fr.daliush.shardbound.dataset.unanswerable;

import java.util.List;

/**
 * One line of {@code unanswerable.jsonl}: a question the Arbiter must decline. {@code source} is "invented", for a
 * keyword, card or mechanic that does not exist, called {@code name}; or "open-point", for a point of
 * 12-open-points.md titled {@code name}. {@code group} decides the split; {@code protectedTerms} are the words a
 * rewording must keep.
 */
public record UnanswerableQuestion(String id, String category, String group, String question, String source,
        String name, List<String> protectedTerms) {

    public UnanswerableQuestion {
        protectedTerms = List.copyOf(protectedTerms);
    }
}
