package fr.daliush.shardbound.dataset.rule;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.RULEBOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleQuestionsTest {

    private final RuleQuestions questions = new RuleQuestions(RULEBOOK);

    @Test
    void readsTheQuestionsWrittenOnceWithTheSentencesOfTheirRule() {
        List<RuleQuestion> all = questions.all();

        assertThat(all).extracting(RuleQuestion::id).doesNotHaveDuplicates();
        assertThat(all).allSatisfy(question -> {
            assertThat(question.category()).isEqualTo("simple-rule");
            assertThat(question.id()).isEqualTo("rule/" + question.rule());
            assertThat(question.keyFacts()).isNotEmpty();
        });
        assertThat(all).filteredOn(question -> question.rule().equals("7.5")).singleElement()
                .satisfies(question -> assertThat(question.keyFacts()).contains("A frozen unit cannot intercept."));
    }

    @Test
    void rejectsAQuestionWhoseRuleChangedSinceItWasWritten() {
        RuleQuestion stale = new RuleQuestion("rule/7.5", "simple-rule", "7.5", "Can a frozen unit intercept?",
                "Intercept: a frozen unit can intercept.", List.of("A frozen unit can intercept."));

        assertThatThrownBy(() -> questions.checked(List.of(stale)))
                .hasMessageContaining("rule/7.5: rule 7.5 changed since its question was written");
    }

    @Test
    void rejectsAQuestionAboutARuleThatIsNotInTheRulebook() {
        RuleQuestion missing = new RuleQuestion("rule/8.99", "simple-rule", "8.99", "?", "", List.of("Fact."));

        assertThatThrownBy(() -> questions.checked(List.of(missing)))
                .hasMessageContaining("rule 8.99 is not in the rulebook");
    }
}
