package fr.daliush.shardbound.core.bot.greedy;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.Pick;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.testing.TestCards;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** An action is scored once it has resolved (spec §10): the bot's own choices greedily, the opponent's first option. */
class ActionOutcomeTest {

    private final Evaluator evaluator = new Evaluator(TestCards.CATALOG);
    private final ActionOutcome outcome = new ActionOutcome(ENGINE, evaluator, P1);

    @Test
    void anAttackIsScoredAfterItsDamageAsIfTheDefenderDidNotIntercept() {
        GameState world = mainDecisionOf(scenario().shards(P1, 1).unit(P1, "ember.cinderling")
                .unit(P2, "neutral.shard-construct").unit(P2, "neutral.shardling").build());
        Action attack = Choices.attack("ember.cinderling").on(Pick.unit("neutral.shard-construct"))
                .pick(world, decision(world));

        GameState paused = ENGINE.apply(world, attack).state();
        assertThat(decision(paused).kind()).isEqualTo(DecisionKind.INTERCEPT);
        GameState declined = ENGINE.apply(paused, new Action.DeclineIntercept()).state();

        assertThat(outcome.score(world, attack)).isEqualTo(evaluator.score(declined, P1))
                .isEqualTo(evaluator.score(paused, P1) + 3);
    }

    @Test
    void theBotsOwnChoicesInsideTheActionAreMadeGreedily() {
        GameState world = mainDecisionOf(scenario().shards(P1, 1).hand(P1, "test.watcher")
                .unit(P2, "ember.cinderling").unit(P2, "neutral.shardling").build());
        Action play = Choices.play("test.watcher").pick(world, decision(world));

        // 10.6: the Watcher's arrival ability asks its two targets, one after the other.
        GameState firstTarget = ENGINE.apply(world, play).state();
        double best = Double.NEGATIVE_INFINITY;
        for (Action first : decision(firstTarget).actions()) {
            GameState secondTarget = ENGINE.apply(firstTarget, first).state();
            assertThat(decision(secondTarget).player()).isEqualTo(P1);
            for (Action second : decision(secondTarget).actions()) {
                best = Math.max(best, evaluator.score(ENGINE.apply(secondTarget, second).state(), P1));
            }
        }
        GameState firstOptions = firstOptionOf(firstOptionOf(firstTarget));

        assertThat(decision(firstOptions).kind()).isEqualTo(DecisionKind.MAIN);
        assertThat(outcome.score(world, play)).isEqualTo(best).isGreaterThan(evaluator.score(firstOptions, P1));
    }

    @Test
    void aTieGoesToTheLowestIndex() {
        GameState world = mainDecisionOf(scenario().shards(P1, 2)
                .unit(P1, "neutral.shardling").unit(P1, "neutral.shardling").build());
        List<Action> attacks = decision(world).actions().stream()
                .filter(action -> action instanceof Action.Attack attack
                        && attack.target().equals(Optional.of(TargetRef.player(P2))))
                .toList();
        assertThat(attacks).hasSize(2);

        assertThat(outcome.best(world, attacks).action()).isEqualTo(attacks.getFirst());
    }

    private static GameState firstOptionOf(GameState state) {
        return ENGINE.apply(state, decision(state).actions().getFirst()).state();
    }

    private static GameState mainDecisionOf(GameState start) {
        return ENGINE.resume(start).state();
    }

    private static Decision decision(GameState state) {
        return ENGINE.decision(state).orElseThrow();
    }
}
