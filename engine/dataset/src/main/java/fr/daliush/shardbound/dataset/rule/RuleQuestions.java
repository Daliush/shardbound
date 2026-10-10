package fr.daliush.shardbound.dataset.rule;

import fr.daliush.shardbound.dataset.FrozenQuestions;
import fr.daliush.shardbound.dataset.Rulebook;
import java.util.List;

/**
 * The simple-rule questions (design doc §6.1), one per rule of sections 1 to 11, written once and kept in
 * {@code questions/rules.jsonl}. A rule that changed since its question was written stops the build: its key facts
 * would be out of date.
 */
public final class RuleQuestions {

    public static final String RESOURCE = "/questions/rules.jsonl";

    private final Rulebook rulebook;

    public RuleQuestions(Rulebook rulebook) {
        this.rulebook = rulebook;
    }

    public List<RuleQuestion> all() {
        return checked(FrozenQuestions.read(RESOURCE, RuleQuestion.class));
    }

    public List<RuleQuestion> checked(List<RuleQuestion> questions) {
        for (RuleQuestion question : questions) {
            if (!rulebook.has(question.rule())) {
                throw new IllegalStateException(question.id() + ": rule " + question.rule()
                        + " is not in the rulebook");
            }
            if (!rulebook.text(question.rule()).orElseThrow().equals(question.ruleText())) {
                throw new IllegalStateException(question.id() + ": rule " + question.rule() + " changed since its "
                        + "question was written; write its question and key facts again in " + RESOURCE);
            }
        }
        return questions;
    }
}
