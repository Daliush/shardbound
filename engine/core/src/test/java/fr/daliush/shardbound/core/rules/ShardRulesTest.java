package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.Shards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 4: Shards. */
class ShardRulesTest {

    @Test
    @DisplayName("4.1 — max Shards grow by 1 at the start of each turn, up to 10")
    void maxShardsGrow() {
        ScenarioResult growing = run(scenario().maxShards(P2, 3).build(), endTurn());
        ScenarioResult capped = run(scenario().maxShards(P2, 10).build(), endTurn());

        assertThat(growing.player(P2).shards().max()).isEqualTo(4);
        assertThat(capped.player(P2).shards().max()).isEqualTo(10);
        assertThat(growing.events(GameEvent.ShardsRefilled.class).getFirst().rules()).containsExactly("4.1", "4.2");
    }

    @Test
    @DisplayName("4.2 — Shards are refilled up to the max at the start of the turn")
    void refill() {
        ScenarioResult result = run(scenario().maxShards(P2, 5).build(), endTurn());

        assertThat(result.player(P2).shards()).isEqualTo(new Shards(6, 6, 0));
    }

    @Test
    @DisplayName("4.3 — playing a card or attacking costs Shards; what a player cannot pay is not legal")
    void costs() {
        GameState broke = scenario().shards(P1, 0).hand(P1, "neutral.shardling").unit(P1, "neutral.shardling").build();
        ScenarioResult paid = run(scenario().shards(P1, 3).hand(P1, "neutral.shardling").build(),
                play("neutral.shardling"));

        assertThat(ENGINE.resume(broke).state().pending().orElseThrow().actions())
                .containsExactly(new Action.EndTurn());
        assertThat(paid.player(P1).shards().available()).isEqualTo(2);
    }

    @Test
    @DisplayName("4.4 — unspent Shards are lost at the end of the turn")
    void unspentShardsAreLost() {
        ScenarioResult result = run(scenario().shards(P1, 4).build(), endTurn());

        assertThat(result.player(P1).shards().available()).isZero();
        assertThat(result.player(P1).shards().max()).isEqualTo(4);
    }
}
