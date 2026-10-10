package fr.daliush.shardbound.dataset.unanswerable;

import static fr.daliush.shardbound.dataset.testing.DatasetTesting.CATALOG;
import static fr.daliush.shardbound.dataset.testing.DatasetTesting.RULEBOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnanswerableQuestionsTest {

    private final UnanswerableQuestions questions = new UnanswerableQuestions(CATALOG, RULEBOOK);

    @Test
    void readsTheQuestionsWrittenOnceAndFindsThemStillUnanswerable() {
        List<UnanswerableQuestion> all = questions.all();

        assertThat(all).extracting(UnanswerableQuestion::id).doesNotHaveDuplicates();
        assertThat(all).extracting(UnanswerableQuestion::source).containsOnly("invented", "open-point");
        assertThat(all).filteredOn(question -> question.source().equals("invented"))
                .allSatisfy(question -> assertThat(question.protectedTerms()).containsExactly(question.name()));
    }

    @Test
    void rejectsAnInventedNameThatACardUses() {
        assertThatThrownBy(() -> questions.checked(List.of(invented("Sentinel"))))
                .hasMessageContaining("Root Sentinel uses Sentinel");
    }

    @Test
    void rejectsAnInventedNameThatTheRulebookUses() {
        assertThatThrownBy(() -> questions.checked(List.of(invented("Fatigue"))))
                .hasMessageContaining("the rulebook uses Fatigue");
    }

    @Test
    void rejectsAnOpenPointThatIsSettled() {
        UnanswerableQuestion settled = new UnanswerableQuestion("unanswerable/open-point/x/1", "unanswerable",
                "open-point/x", "?", "open-point", "Doom", List.of());

        assertThatThrownBy(() -> questions.checked(List.of(settled)))
                .hasMessageContaining("the open point Doom is settled or gone");
    }

    private static UnanswerableQuestion invented(String name) {
        return new UnanswerableQuestion("unanswerable/test/1", "unanswerable", "invented/test",
                "What does " + name + " do?", "invented", name, List.of(name));
    }
}
