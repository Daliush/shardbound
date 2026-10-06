package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook sections 5.2 to 5.5: start of turn, main phase, end of turn, the opponent's turn. */
class TurnRulesTest {

    @Test
    @DisplayName("5.2 — the start of turn runs in order: Shards, draw, then \"Turn start\" abilities")
    void startOfTurnOrder() {
        ScenarioResult result = run(scenario().deck(P2, "neutral.shardling")
                        .relic(P2, "root.heartwood-shrine").build(),
                endTurn());

        assertThat(trace(result)).containsSubsequence(
                "TurnStarted[5.2]", "ShardsRefilled[4.1, 4.2]", "CardDrawn[5.2.3]", "AbilityTriggered[9.5]",
                "TokenSummoned[8.9]");
    }

    @Test
    @DisplayName("5.3 — the main phase is asked even when ending the turn is the only option")
    void mainPhaseAlwaysAsked() {
        Decision decision = ENGINE.resume(scenario().build()).state().pending().orElseThrow();

        assertThat(decision.kind()).isEqualTo(DecisionKind.MAIN);
        assertThat(decision.actions()).containsExactly(new Action.EndTurn());
    }

    @Test
    @DisplayName("5.4 — \"Turn end\" abilities resolve, then the turn ends and Shards are lost")
    void endOfTurnOrder() {
        ScenarioResult result = run(scenario().hp(P1, 40).shards(P1, 2).unit(P1, "test.guard").build(), endTurn());

        assertThat(trace(result)).containsSubsequence(
                "AbilityTriggered[9.6]", "PlayerHealed[8.4]", "TurnEnded[5.4]", "TurnStarted[5.2]");
        assertThat(result.player(P1).hp()).isEqualTo(42);
        assertThat(result.player(P1).shards().available()).isZero();
    }

    @Test
    @DisplayName("5.5 — the non-active player only intercepts or answers effects, never plays")
    void opponentTurn() {
        ScenarioResult result = run(scenario().shards(P1, 1).unit(P1, "ember.cinderling")
                        .unit(P2, "neutral.shardling").unit(P2, "root.sprout").build(),
                attack("ember.cinderling").on(unit("neutral.shardling")));

        Decision intercept = result.pending().orElseThrow();
        assertThat(intercept.player()).isEqualTo(P2);
        assertThat(intercept.kind()).isEqualTo(DecisionKind.INTERCEPT);
        assertThat(intercept.actions()).allMatch(action ->
                action instanceof Action.Intercept || action instanceof Action.DeclineIntercept);
    }
}
