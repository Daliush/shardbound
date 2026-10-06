package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.player;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.GameResult.EndReason;
import fr.daliush.shardbound.core.testing.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 1: HP, victory, fatigue, turn limit. */
class GameRulesTest {

    @Test
    @DisplayName("1.1 — each player starts with 50 HP")
    void startingHp() {
        Transition start = new GameEngine(TestContent.catalog()).newGame(new GameSetup(
                TestContent.content().deck("ember-starter"), TestContent.content().deck("root-starter"), 1));

        assertThat(start.state().p1().hp()).isEqualTo(50);
        assertThat(start.state().p2().hp()).isEqualTo(50);
    }

    @Test
    @DisplayName("1.2 — a player whose HP drops to 0 loses")
    void zeroHpLoses() {
        ScenarioResult result = run(scenario()
                        .shards(P1, 1).unit(P1, "ember.cinderling").hp(P2, 3).build(),
                attack("ember.cinderling").on(player(P2)));

        assertThat(result.state().result()).contains(new GameResult.Win(P1, EndReason.HP));
        assertThat(result.events(GameEvent.GameEnded.class).getFirst().rules()).contains("1.2");
    }

    @Test
    @DisplayName("1.3 — both players at 0 HP at the same time is a draw")
    void doubleKnockOut() {
        ScenarioResult result = run(scenario().hp(P1, 0).hp(P2, -2).build());

        assertThat(result.state().result()).contains(new GameResult.Draw(EndReason.DOUBLE_KO));
        assertThat(result.events(GameEvent.GameEnded.class).getFirst().rules()).contains("1.3");
    }

    @Test
    @DisplayName("1.4 — drawing from an empty deck costs 1 HP, then 2, and an empty deck alone does not lose")
    void fatigue() {
        ScenarioResult result = run(scenario().deck(P1, "neutral.shardling").build(),
                endTurn(), endTurn(), endTurn());

        assertThat(result.events(GameEvent.HpLost.class))
                .extracting(GameEvent.HpLost::player, GameEvent.HpLost::amount)
                .containsExactly(tuple(P2, 1), tuple(P2, 2));
        assertThat(result.player(P2).hp()).isEqualTo(47);
        assertThat(result.player(P2).fatigue()).isEqualTo(2);
        assertThat(result.state().isOver()).isFalse();
    }

    @Test
    @DisplayName("1.5 — the game is a draw at the end of each player's 50th turn")
    void turnLimit() {
        ScenarioResult result = run(scenario().turn(100).active(P2).build(), endTurn());

        assertThat(result.state().result()).contains(new GameResult.Draw(EndReason.TURN_LIMIT));
        assertThat(trace(result)).endsWith("TurnEnded[5.4]", "GameEnded[1.5]");
    }

    @Test
    @DisplayName("1.6 — the game ends at once: abilities still waiting never resolve")
    void endsAtOnce() {
        ScenarioResult result = run(scenario()
                        .shards(P1, 5).hand(P1, "ember.cinderfall").hp(P1, 2)
                        .unit(P2, "ember.cinderling").unit(P2, "ember.cinderling").build(),
                play("ember.cinderfall"));

        assertThat(result.events(GameEvent.AbilityTriggered.class)).hasSize(1);
        assertThat(result.events(GameEvent.PlayerDamaged.class)).hasSize(1);
        assertThat(result.state().result()).contains(new GameResult.Win(P2, EndReason.HP));
        assertThat(result.events(GameEvent.GameEnded.class).getFirst().rules()).contains("1.6");
    }
}
