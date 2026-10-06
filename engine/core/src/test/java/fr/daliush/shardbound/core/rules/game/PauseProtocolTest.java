package fr.daliush.shardbound.core.rules.game;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.testing.TestCards;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** How a step stops to let a player choose, and how the answer finds it again. */
class PauseProtocolTest {

    @Test
    void aPausedStepWaitsAtTheFrontOfThePendingWork() {
        ScenarioResult result = run(interceptScenario(), attack("ember.cinderling").on(unit("neutral.shard-construct")));
        GameState paused = result.state();

        assertThat(paused.pending().orElseThrow().kind()).isEqualTo(DecisionKind.INTERCEPT);
        assertThat(paused.resolution().steps().getFirst())
                .isInstanceOfSatisfying(Step.ResolveAttack.class,
                        attack -> assertThat(attack.phase()).isEqualTo(Step.AttackPhase.INTERCEPT));
    }

    @Test
    void decisionsAskedInTheMiddleOfAStepMustPauseIt() {
        Game game = Game.of(interceptScenario(), TestCards.CATALOG);

        assertThatThrownBy(() -> game.ask(P2, DecisionKind.INTERCEPT, List.of(new Action.DeclineIntercept())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pauseAndAsk");
    }

    @Test
    void anAttackCanOnlyBeResumedInItsInterceptPhase() {
        GameState start = interceptScenario();
        Game game = Game.of(start, TestCards.CATALOG);
        Step.ResolveAttack effects = new Step.ResolveAttack(P1, start.p1().units().getFirst().asCard(), 0,
                Optional.empty(), Step.AttackPhase.EFFECTS);

        assertThatThrownBy(() -> StepRunner.resume(game, effects, new Action.DeclineIntercept()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERCEPT phase");
    }

    private static GameState interceptScenario() {
        return scenario().shards(P1, 1).unit(P1, "ember.cinderling")
                .unit(P2, "neutral.shard-construct").unit(P2, "neutral.shardling").build();
    }
}
