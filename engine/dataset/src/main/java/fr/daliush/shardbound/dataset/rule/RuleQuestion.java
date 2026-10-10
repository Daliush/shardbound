package fr.daliush.shardbound.dataset.rule;

import java.util.List;

/**
 * One line of {@code rules.jsonl}: a question a player asks whose answer is one rule, written once. The key facts are
 * the rule's own sentences, examples left out; a rule under a subheading ("5.2 Start of turn") starts each with it,
 * since the heading says when the rule applies. {@code ruleText} is the rule they were read from, so a rule changed
 * since is caught.
 */
public record RuleQuestion(String id, String category, String rule, String question, String ruleText,
        List<String> keyFacts) {

    public RuleQuestion {
        keyFacts = List.copyOf(keyFacts);
    }
}
