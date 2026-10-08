package fr.daliush.shardbound.core.bot.greedy;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.testing.TestCards;
import org.junit.jupiter.api.Test;

/** The starting formula of spec §10, exactly. */
class EvaluatorTest {

    private final Evaluator evaluator = new Evaluator(TestCards.CATALOG);

    @Test
    void hpBoardsUnitCountsAndHandSize() {
        GameState state = scenario().hp(P1, 40).hp(P2, 45)
                .unit(P1, "ember.cinderling")                      // 2 defense, Flick deals 3
                .unit(P2, "neutral.shard-construct")               // 9 defense, Crush deals 7
                .unit(P2, "neutral.shardling")                     // 3 defense, Glint deals 3
                .hand(P1, "test.bolt", "test.bolt")
                .build();

        double expected = (40 - 45) + (2 + 2 * 3) - ((9 + 2 * 7) + (3 + 2 * 3)) + 3 * (1 - 2) + 0.5 * 2;
        assertThat(evaluator.score(state, P1)).isEqualTo(expected);
        assertThat(evaluator.score(state, P2)).isEqualTo((45 - 40) + 32 - 8 + 3 * (2 - 1));
    }

    @Test
    void aDoomedUnitCountsItsZeroDefense() {
        GameState state = scenario().hp(P1, 50).hp(P2, 50)
                .unit(P1, "neutral.shard-construct", unit -> unit.anchorProtected().doomed())
                .build();

        assertThat(evaluator.score(state, P1)).isEqualTo(0 + 2 * 7 + 3);
    }

    @Test
    void theBestAttackDamageCountsWhatAurasAndModifiersChange() {
        GameState state = ENGINE.resume(scenario()
                .unit(P1, "neutral.shard-construct")
                .relic(P2, "test.blight")                          // enemy units get -1/-2
                .build()).state();

        assertThat(evaluator.score(state, P1)).isEqualTo((9 - 2) + 2 * (7 - 1) + 3);
    }

    @Test
    void aWinIsWorthEverythingALossNothingAndADrawZero() {
        GameState won = run(scenario().shards(P1, 1).hand(P1, "test.bolt").hp(P2, 3).build(),
                play("test.bolt")).state();
        GameState drawn = run(scenario().turn(100).active(P2).build(), endTurn()).state();

        assertThat(evaluator.score(won, P1)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(evaluator.score(won, P2)).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(evaluator.score(drawn, P1)).isZero();
    }
}
