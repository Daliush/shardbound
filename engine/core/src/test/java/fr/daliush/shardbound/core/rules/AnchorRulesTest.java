package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Choices.play;
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
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook 11.3: Anchor, with the start and end of turn rules it adds (5.2.1, 5.4.3) and its exception to 3.8. */
class AnchorRulesTest {

    @Test
    @DisplayName("11.3.1 — an anchored unit is protected from its arrival until its controller's next turn")
    void protectedUntilNextTurn() {
        GameState start = scenario().shards(P1, 2).hand(P1, "root.root-sentinel")
                .deck(P1, "neutral.shardling").deck(P2, "neutral.shardling").build();

        ScenarioResult opponentsTurn = run(start, play("root.root-sentinel"), endTurn());
        ScenarioResult nextTurn = run(start, play("root.root-sentinel"), endTurn(), endTurn());

        assertThat(trace(opponentsTurn)).contains("UnitArrived[6.3, 6.5, 11.3.1]");
        assertThat(opponentsTurn.unit("root.root-sentinel").anchorProtected()).isTrue();
        assertThat(nextTurn.unit("root.root-sentinel").anchorProtected()).isFalse();
        assertThat(trace(nextTurn)).containsSubsequence("TurnStarted[5.2]", "AnchorProtectionEnded[5.2.1, 11.3.1]");
    }

    @Test
    @DisplayName("5.2.1 — the active player's Anchor protections end first, before Shards and the draw")
    void protectionEndsFirst() {
        GameState start = scenario().turn(4).active(P2).deck(P1, "neutral.shardling")
                .unit(P1, "root.root-sentinel", unit -> unit.anchorProtected())
                .unit(P2, "root.oakheart-guardian", unit -> unit.anchorProtected()).build();

        ScenarioResult result = run(start, endTurn());

        assertThat(trace(result)).containsSubsequence("TurnStarted[5.2]", "AnchorProtectionEnded[5.2.1, 11.3.1]",
                "ShardsRefilled[4.1, 4.2]", "CardDrawn[5.2.3]");
        assertThat(result.unit("root.root-sentinel").anchorProtected()).isFalse();
        assertThat(result.unit("root.oakheart-guardian").anchorProtected()).as("the opponent's unit").isTrue();
    }

    @Test
    @DisplayName("11.3.2 — neither destroy nor return to hand makes a protected unit leave the board")
    void cannotLeaveTheBoard() {
        ScenarioResult destroyed = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P2, "root.root-sentinel", unit -> unit.anchorProtected()).build(),
                play("neutral.crystal-rupture").on(unit("root.root-sentinel")));
        ScenarioResult returned = run(scenario().shards(P1, 3).hand(P1, "tide.receding-wave")
                        .unit(P2, "root.root-sentinel", unit -> unit.anchorProtected()).build(),
                play("tide.receding-wave").on(unit("root.root-sentinel")));

        assertThat(destroyed.player(P2).units()).hasSize(1);
        assertThat(trace(destroyed)).contains("AnchorPrevented[11.3.2, 11.3.3]").doesNotContain("UnitDestroyed[8.2]");
        assertThat(returned.player(P2).units()).hasSize(1);
        assertThat(returned.player(P2).hand()).isEmpty();
        assertThat(trace(returned)).contains("AnchorPrevented[11.3.2, 11.3.3]");
    }

    @Test
    @DisplayName("11.3.2 — Tempest destroys every unit but the protected one")
    void tempest() {
        ScenarioResult result = run(scenario().shards(P1, 7).hand(P1, "neutral.tempest").unit(P1, "neutral.shardling")
                        .unit(P2, "root.root-sentinel", unit -> unit.anchorProtected()).unit(P2, "root.sprout").build(),
                play("neutral.tempest"));

        assertThat(result.player(P1).units()).isEmpty();
        assertThat(result.player(P2).units()).extracting(Unit::card).extracting(Object::toString)
                .containsExactly("root.root-sentinel");
    }

    @Test
    @DisplayName("11.3.3 — a protected unit sacrificed to pay a card counts as paid and stays; the card resolves")
    void sacrificeCountsButStays() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.pyre-offering")
                        .unit(P1, "root.root-sentinel", unit -> unit.anchorProtected())
                        .unit(P2, "neutral.shard-construct").build(),
                play("ember.pyre-offering").on(unit("neutral.shard-construct"))
                        .sacrificing(unit("root.root-sentinel")));

        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3]", "AnchorPrevented[11.3.2, 11.3.3]",
                "UnitDamaged[8.1]", "SpellResolved[6.3, 3.5]");
        assertThat(result.unit("root.root-sentinel").defense()).isEqualTo(5);
        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(1);
    }

    @Test
    @DisplayName("11.3.4 — a protected unit at 0 defense is doomed: it stays, and can still intercept and attack")
    void doomed() {
        GameState start = scenario().shards(P1, 2).hand(P1, "ember.pyre-offering")
                .unit(P1, "neutral.shardling").unit(P1, "ember.cinderling")
                .unit(P2, "root.root-sentinel", unit -> unit.anchorProtected()).unit(P2, "root.sprout").build();

        ScenarioResult result = run(start,
                play("ember.pyre-offering").on(unit("root.root-sentinel")).sacrificing(unit("neutral.shardling")),
                attack("ember.cinderling").on(unit("root.sprout")));

        Unit sentinel = result.unit("root.root-sentinel");
        assertThat(sentinel.defense()).isZero();
        assertThat(sentinel.doomed()).isTrue();
        assertThat(trace(result)).containsSubsequence("UnitDamaged[8.1]", "UnitDoomed[11.3.4]")
                .doesNotContain("UnitDestroyed[6.6]");
        Decision intercept = result.pending().orElseThrow();
        assertThat(intercept.kind()).isEqualTo(DecisionKind.INTERCEPT);
        assertThat(intercept.actions()).contains(new Action.Intercept(sentinel.id()));

        GameState doomedAttacker = scenario().shards(P1, 2)
                .unit(P1, "root.root-sentinel", unit -> unit.anchorProtected().doomed()).unit(P2, "root.sprout").build();
        assertThat(ENGINE.resume(doomedAttacker).state().pending().orElseThrow().actions())
                .anyMatch(Action.Attack.class::isInstance);
    }

    @Test
    @DisplayName("11.3.4 — a doomed unit back above 0 defense is no longer doomed, and is doomed again back at 0")
    void doomLiftedAndBack() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "test.ward", "ember.spark-dart")
                        .unit(P2, "root.root-sentinel", unit -> unit.anchorProtected().doomed()).build(),
                play("test.ward").on(unit("root.root-sentinel")),
                play("ember.spark-dart").on(unit("root.root-sentinel")));

        assertThat(trace(result)).containsSubsequence("Modified[8.5]", "DoomLifted[11.3.4]", "UnitDamaged[8.1]",
                "UnitDoomed[11.3.4]");
        assertThat(result.unit("root.root-sentinel").doomed()).isTrue();
    }

    @Test
    @DisplayName("6.9 — maluses can take a doomed unit's max defense below 0; its defense stays at 0")
    void maxDefenseBelowZero() {
        GameState start = scenario().shards(P1, 3).hand(P1, "test.hex", "test.hex", "test.hex")
                .deck(P2, "neutral.shardling")
                .unit(P2, "root.root-sentinel", unit -> unit.anchorProtected().doomed()).build();

        ScenarioResult hexed = run(start, play("test.hex").on(unit("root.root-sentinel")),
                play("test.hex").on(unit("root.root-sentinel")), play("test.hex").on(unit("root.root-sentinel")));
        ScenarioResult expired = run(start, play("test.hex").on(unit("root.root-sentinel")),
                play("test.hex").on(unit("root.root-sentinel")), play("test.hex").on(unit("root.root-sentinel")),
                endTurn());

        assertThat(hexed.unit("root.root-sentinel").maxDefense()).isEqualTo(-1);
        assertThat(hexed.unit("root.root-sentinel").defense()).isZero();
        // 8.17: each malus that ends gives back what it took, on both defenses.
        assertThat(expired.unit("root.root-sentinel").defense()).isEqualTo(5);
        assertThat(trace(expired)).containsSubsequence("ModifierExpired[5.4.2, 8.17]", "DoomLifted[11.3.4]");
    }

    @Test
    @DisplayName("11.3.5 — at the end of the turn its protection ended, a doomed unit still at 0 is destroyed")
    void doomedUnitDestroyedAtEndOfTurn() {
        ScenarioResult result = run(scenario().unit(P1, "root.root-sentinel", unit -> unit.doomed()).build(),
                endTurn());

        assertThat(trace(result)).containsSubsequence("UnitDestroyed[5.4.3, 11.3.5]", "TurnEnded[5.4]");
        assertThat(result.player(P1).units()).isEmpty();
    }

    @Test
    @DisplayName("11.3.5 — a \"Turn end\" heal resolves first, so it can still save a doomed unit")
    void turnEndHealSaves() {
        ScenarioResult result = run(scenario().relic(P1, "test.tidepool")
                        .unit(P1, "root.root-sentinel", unit -> unit.doomed()).build(),
                endTurn());

        assertThat(trace(result)).containsSubsequence("AbilityTriggered[9.6]", "UnitHealed[8.4]",
                "DoomLifted[11.3.4]", "TurnEnded[5.4]").doesNotContain("UnitDestroyed[5.4.3, 11.3.5]");
        assertThat(result.unit("root.root-sentinel").defense()).isEqualTo(2);
    }

    @Test
    @DisplayName("5.4.3 — doomed units are destroyed after \"Turn end\" abilities; their Death abilities resolve "
            + "before the turn ends")
    void doomedUnitsAtEndOfTurn() {
        ScenarioResult result = run(scenario().deck(P2, "neutral.shardling").unit(P1, "test.guard")
                        .unit(P1, "ember.cinderling", unit -> unit.doomed()).build(),
                endTurn());

        assertThat(trace(result)).containsSubsequence("AbilityTriggered[9.6]", "UnitDestroyed[5.4.3, 11.3.5]",
                "AbilityTriggered[9.3]", "PlayerDamaged[8.1]", "TurnEnded[5.4]", "TurnStarted[5.2]");
        assertThat(result.player(P2).hp()).isEqualTo(48);
    }

    @Test
    @DisplayName("5.4.3 — a unit doomed during its arrival turn is still protected at the end of that turn")
    void doomedOnArrivalTurn() {
        ScenarioResult result = run(scenario()
                        .unit(P1, "root.root-sentinel", unit -> unit.arrivedThisTurn().anchorProtected().doomed())
                        .build(),
                endTurn());

        assertThat(result.unit("root.root-sentinel").doomed()).isTrue();
        assertThat(trace(result)).doesNotContain("UnitDestroyed[5.4.3, 11.3.5]");
    }

    @Test
    @DisplayName("11.3.6 — once its protection is over, a unit is destroyed normally")
    void destroyedOnceUnprotected() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.pyre-offering").unit(P1, "root.sprout")
                        .unit(P2, "root.root-sentinel").build(),
                play("ember.pyre-offering").on(unit("root.root-sentinel")).sacrificing(unit("root.sprout")));

        assertThat(result.player(P2).units()).isEmpty();
        assertThat(trace(result)).contains("UnitDestroyed[6.6]").doesNotContain("UnitDoomed[11.3.4]");
    }

    @Test
    @DisplayName("3.8 — on a full board, sacrificing a protected unit frees no place for the unit played")
    void anchoredSacrificeFreesNoPlace() {
        GameState start = scenario().shards(P1, 3).hand(P1, "ember.flamebound-zealot")
                .unit(P1, "root.root-sentinel", unit -> unit.anchorProtected())
                .unit(P1, "root.sprout").unit(P1, "root.sprout").unit(P1, "root.sprout")
                .unit(P1, "root.sprout").unit(P1, "root.sprout").build();

        Decision main = ENGINE.resume(start).state().pending().orElseThrow();

        InstanceId sentinel = start.p1().units().getFirst().id();
        assertThat(main.actions()).filteredOn(Action.PlayCard.class::isInstance).hasSize(5)
                .noneMatch(action -> ((Action.PlayCard) action).sacrificed().contains(sentinel));
    }
}
