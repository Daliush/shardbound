package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choice;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.function.Predicate;

/**
 * One concrete game of an interaction: the board, the choices played from it, the player who asks the question, and
 * the question as a template sentence. Its {@code id} is stable, "anchor+destroy/opponents/protected/…", so a dataset
 * version can be compared with the next one.
 * <p>
 * {@code answers} selects the events of the outcome that answer the question: their sentences become the key facts,
 * their rules the essential rules. {@code contextRules} explain the situation rather than what happens, so no event
 * carries them: "protected since it arrived" (11.3.1), "its protection is over" (11.3.6). A correct answer cites them
 * too.
 */
public record Scenario(String id, GameState start, List<Choice> choices, PlayerId asker, String question,
        Predicate<GameEvent> answers, List<String> contextRules) {

    public Scenario {
        choices = List.copyOf(choices);
        contextRules = List.copyOf(contextRules);
    }

    public Choice[] choicesToPlay() {
        return choices.toArray(Choice[]::new);
    }
}
