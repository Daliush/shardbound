package fr.daliush.shardbound.dataset.rule;

import java.util.List;

/**
 * One line of {@code rules.jsonl}: a question a player asks whose answer is one rule. Luna 6 wrote the question once,
 * for the dataset v1. The key facts are the rule's own sentences, and {@code ruleText} the rule they were read from, so
 * a rule changed since is caught.
 */
public record RuleQuestion(String id, String category, String rule, String question, String ruleText,
        List<String> keyFacts) {

    public RuleQuestion {
        keyFacts = List.copyOf(keyFacts);
    }
}
