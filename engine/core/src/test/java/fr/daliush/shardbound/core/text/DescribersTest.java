package fr.daliush.shardbound.core.text;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.testing.TestCards;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DescribersTest {

    private final EventDescriber events = new EventDescriber(TestCards.CATALOG);
    private final ActionDescriber actions = new ActionDescriber(TestCards.CATALOG);

    @Test
    void describesEventsFromEachPlayersPointOfView() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "ember.spark-dart")
                        .unit(P1, "ember.cinderling").unit(P2, "root.sprout").unit(P2, "neutral.shard-construct").build(),
                play("ember.spark-dart").on(unit("root.sprout")),
                attack("ember.cinderling").on(unit("neutral.shard-construct")));

        assertThat(describe(result, P1)).contains(
                "You play Spark Dart (1 Shard).",
                "Sprout #3 takes 3 damage.",
                "Sprout #3 is destroyed.",
                "Sprout #3 vanishes.",
                "Spark Dart goes to your graveyard.",
                "Cinderling #2 attacks Shard Construct #4 with Flick.");
        assertThat(describe(result, P2)).contains(
                "Your opponent plays Spark Dart (1 Shard).",
                "Spark Dart goes to your opponent's graveyard.");
    }

    @Test
    void hidesTheCardAnOpponentDraws() {
        GameEvent.CardDrawn drawn = new GameEvent.CardDrawn(P2, Optional.empty(), List.of("5.2.3"));

        assertThat(events.describe(drawn, P1)).isEqualTo("Your opponent draws a card.");
    }

    @Test
    void labelsTheActionsOfADecision() {
        GameState start = scenario().shards(P1, 1).hand(P1, "ember.spark-dart").unit(P1, "ember.cinderling")
                .unit(P2, "root.sprout").build();
        GameState state = ENGINE.resume(start).state();
        Decision decision = state.pending().orElseThrow();

        assertThat(decision.actions()).extracting(action -> actions.describe(action, state, P1)).containsExactly(
                "Play Spark Dart (1 Shard) on Sprout #3",
                "Cinderling #2 attacks Sprout #3 with Flick (1 Shard)",
                "End your turn");
    }

    private List<String> describe(ScenarioResult result, PlayerId viewer) {
        return ENGINE.eventsFor(result.events(), viewer).stream().map(event -> events.describe(event, viewer)).toList();
    }
}
