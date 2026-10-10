package fr.daliush.shardbound.dataset.answer;

import java.util.List;

/**
 * What an answer must say: {@code keyFacts} are sentences from the asker's point of view, {@code rules} the essential
 * rules in the order they applied, and {@code trace} every event, incidental ones included ("CardPlayed[6.3]").
 */
public record AnswerKey(List<String> keyFacts, List<String> rules, List<String> trace) {

    public AnswerKey {
        keyFacts = List.copyOf(keyFacts);
        rules = List.copyOf(rules);
        trace = List.copyOf(trace);
    }
}
