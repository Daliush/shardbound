package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.chooseTarget;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 10: targets. */
class TargetRulesTest {

    @Test
    @DisplayName("10.2 — the controller chooses a triggered ability's target when it resolves")
    void abilityTargetChosenOnResolution() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.watcher")
                        .unit(P2, "neutral.shard-construct").unit(P2, "root.thornback-ancient").build(),
                play("test.watcher"));

        Decision choice = result.pending().orElseThrow();
        assertThat(choice.kind()).isEqualTo(DecisionKind.CHOOSE_TARGET);
        assertThat(choice.player()).isEqualTo(P1);
        assertThat(choice.actions()).hasSize(2);
    }

    @Test
    @DisplayName("10.3 — an effect with no valid target does nothing; the card can still be played")
    void noValidTarget() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.spark-dart").build(),
                play("ember.spark-dart"));

        assertThat(result.events(GameEvent.UnitDamaged.class)).isEmpty();
        assertThat(trace(result)).contains("SpellResolved[6.3, 3.5]");
    }

    @Test
    @DisplayName("10.5 — a spell's targets are chosen when it is played; one that became invalid does nothing")
    void spellTargetsChosenOnPlay() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.double-strike")
                        .unit(P2, "neutral.shard-construct").unit(P2, "root.thornback-ancient").build(),
                play("test.double-strike").on(unit("neutral.shard-construct"), unit("neutral.shard-construct")));

        assertThat(result.player(P2).units()).hasSize(1);
        assertThat(result.events(GameEvent.UnitDamaged.class)).isEmpty();
        assertThat(result.decisions().getFirst().actions()).hasSize(4 + 1);
    }

    @Test
    @DisplayName("10.6 — a triggered ability picks all its targets before its first effect, asking only for real choices")
    void abilityPicksAllTargetsFirst() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.watcher")
                        .unit(P2, "neutral.shard-construct").unit(P2, "root.thornback-ancient").build(),
                play("test.watcher"),
                chooseTarget(unit("neutral.shard-construct")),
                chooseTarget(unit("root.thornback-ancient")));

        assertThat(result.decisions()).extracting(Decision::kind)
                .containsExactly(DecisionKind.MAIN, DecisionKind.CHOOSE_TARGET, DecisionKind.CHOOSE_TARGET);
        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(7);
        assertThat(result.unit("root.thornback-ancient").defense()).isEqualTo(14);

        ScenarioResult single = run(scenario().shards(P1, 1).hand(P1, "test.watcher")
                        .unit(P2, "neutral.shard-construct").build(),
                play("test.watcher"));
        assertThat(single.unit("neutral.shard-construct").defense()).isEqualTo(6);
        assertThat(single.pending().orElseThrow().kind()).isEqualTo(DecisionKind.MAIN);
    }
}
