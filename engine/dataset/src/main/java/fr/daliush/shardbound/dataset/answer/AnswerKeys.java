package fr.daliush.shardbound.dataset.answer;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.text.EventDescriber;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import java.util.List;
import java.util.stream.Stream;

/** Reads a played scenario's outcome through what its question asks. */
public final class AnswerKeys {

    private final EventDescriber describer;

    public AnswerKeys(CardCatalog catalog) {
        this.describer = EventDescriber.withoutInstanceIds(catalog);
    }

    /** @throws IllegalStateException if no event of the outcome answers the question: its recipe is wrong */
    public AnswerKey of(Scenario scenario, ScenarioResult result) {
        return switch (scenario.answer()) {
            case Answer.Events events -> fromEvents(scenario, result, events);
            case Answer.Legality legality -> new AnswerKey(List.of(isLegal(result, legality)
                    ? legality.can() : legality.cannot()), List.of(), result.trace());
        };
    }

    private AnswerKey fromEvents(Scenario scenario, ScenarioResult result, Answer.Events events) {
        List<GameEvent> keyEvents = result.events().stream().filter(events.select()).toList();
        if (keyEvents.isEmpty()) {
            throw new IllegalStateException(scenario.id() + ": no event answers the question in " + result.trace());
        }
        // The asker only knows what they would see in a game: another player's draw names no card (3.3).
        List<String> keyFacts = Stream.concat(
                        keyEvents.stream().map(event -> describer.describe(event.seenBy(scenario.asker()),
                                scenario.asker())),
                        events.finalState().apply(result.state()).stream())
                .distinct().toList();
        return new AnswerKey(keyFacts,
                keyEvents.stream().flatMap(event -> event.rules().stream()).distinct().toList(),
                result.trace());
    }

    /** Whether the move is one of the actions of the decision the game waits for at the end. */
    private static boolean isLegal(ScenarioResult result, Answer.Legality legality) {
        return result.pending().stream().flatMap(decision -> decision.actions().stream())
                .anyMatch(action -> legality.move().test(result.state(), action));
    }
}
