package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.declineIntercept;
import static fr.daliush.shardbound.core.scenario.Choices.discard;
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
import static org.assertj.core.api.Assertions.tuple;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.Shards;
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
    @DisplayName("5.2.2 — the active player gains a max Shard and refills their Shards at the start of their turn")
    void shardsAtStartOfTurn() {
        ScenarioResult result = run(scenario().turn(4).active(P2).maxShards(P1, 3).deck(P1, "neutral.shardling")
                        .build(),
                endTurn());

        assertThat(result.player(P1).shards()).isEqualTo(new Shards(4, 4, 0));
        assertThat(trace(result)).containsSubsequence("TurnStarted[5.2]", "ShardsRefilled[4.1, 4.2]",
                "CardDrawn[5.2.3]");
    }

    @Test
    @DisplayName("5.2.4 — only the active player's \"Turn start\" abilities trigger, after the draw, oldest first")
    void turnStartAbilities() {
        ScenarioResult result = run(scenario().turn(4).active(P2).deck(P1, "neutral.shardling")
                        .relic(P1, "root.heartwood-shrine").relic(P2, "root.heartwood-shrine")
                        .relic(P1, "root.heartwood-shrine").build(),
                endTurn());

        assertThat(trace(result)).containsSubsequence("CardDrawn[5.2.3]", "AbilityTriggered[9.5]",
                "TokenSummoned[8.9]", "AbilityTriggered[9.5]", "TokenSummoned[8.9]");
        assertThat(result.events(GameEvent.AbilityTriggered.class)).extracting(event -> event.source().id())
                .containsExactly(InstanceId.of(2), InstanceId.of(4));
        assertThat(result.player(P2).units()).isEmpty();
    }

    @Test
    @DisplayName("5.3.1 — in the main phase, the active player plays cards and attacks in any order, as Shards allow")
    void playAndAttackInAnyOrder() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "ember.spark-dart").unit(P1, "neutral.shardling")
                        .unit(P2, "root.sprout").unit(P2, "neutral.shard-construct").build(),
                attack("neutral.shardling").on(unit("neutral.shard-construct")), declineIntercept(),
                play("ember.spark-dart").on(unit("root.sprout")));

        assertThat(trace(result)).containsSubsequence("AttackDeclared[7.4]", "UnitDamaged[8.1]", "CardPlayed[6.3]",
                "UnitDamaged[8.1]");
        assertThat(result.pending().orElseThrow().actions()).as("no Shard left").containsExactly(new Action.EndTurn());
    }

    @Test
    @DisplayName("5.3.2 — the active player ends their turn whenever they decide: it is always the last option")
    void endTurnWhenever() {
        GameState start = scenario().shards(P1, 5).hand(P1, "ember.spark-dart", "neutral.shardling")
                .unit(P1, "neutral.shardling").unit(P2, "root.sprout").deck(P2, "neutral.shardling").build();

        Decision main = ENGINE.resume(start).state().pending().orElseThrow();
        ScenarioResult result = run(start, endTurn());

        assertThat(main.actions()).hasSizeGreaterThan(1).last().isEqualTo(new Action.EndTurn());
        assertThat(result.state().active()).isEqualTo(P2);
        assertThat(result.player(P1).hand()).hasSize(2);
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
    @DisplayName("5.4.1 — only the active player's \"Turn end\" abilities trigger, first of the end of turn")
    void turnEndAbilities() {
        ScenarioResult result = run(scenario().hp(P1, 40).hp(P2, 40).unit(P1, "test.guard").unit(P2, "test.guard")
                        .deck(P2, "neutral.shardling").build(),
                endTurn());

        assertThat(result.events(GameEvent.AbilityTriggered.class)).singleElement()
                .matches(event -> event.source().owner() == P1, "P1's guard");
        assertThat(trace(result)).containsSubsequence("AbilityTriggered[9.6]", "PlayerHealed[8.4]", "TurnEnded[5.4]");
        assertThat(result.player(P2).hp()).isEqualTo(40);
    }

    @Test
    @DisplayName("5.4.2 — \"until end of turn\" effects end once the \"Turn end\" abilities have resolved")
    void temporaryEffectsEnd() {
        ScenarioResult result = run(scenario().hp(P1, 40).shards(P1, 2).hand(P1, "ember.roaring-pyre")
                        .unit(P1, "test.guard").build(),
                play("ember.roaring-pyre"), endTurn());

        assertThat(trace(result)).containsSubsequence("Modified[8.5]", "AbilityTriggered[9.6]", "PlayerHealed[8.4]",
                "ModifierExpired[5.4.2, 8.5]", "TurnEnded[5.4]");
        assertThat(result.unit("test.guard").attackBonus()).isZero();
    }

    @Test
    @DisplayName("5.4.4 — the Shards the active player did not spend are lost at the end of their turn")
    void unspentShardsLost() {
        ScenarioResult result = run(scenario().shards(P1, 5).deck(P2, "neutral.shardling").build(), endTurn());

        assertThat(result.player(P1).shards()).isEqualTo(new Shards(5, 0, 0));
    }

    @Test
    @DisplayName("5.5.1 — a player never plays a card during the opponent's turn: they only intercept or answer")
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

    @Test
    @DisplayName("5.5.2 — during the opponent's turn, a player makes the choices that effects ask of them")
    void choicesOnOpponentsTurn() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "tide.brinesong").deck(P1, "neutral.shardling")
                        .hand(P2, "neutral.shardling", "ember.spark-dart").build(),
                play("tide.brinesong"), discard("ember.spark-dart"));

        assertThat(result.decisions()).extracting(Decision::player, Decision::kind)
                .containsExactly(tuple(P1, DecisionKind.MAIN), tuple(P2, DecisionKind.CHOOSE_CARDS));
        assertThat(result.player(P2).hand()).extracting(card -> card.card().card().value())
                .containsExactly("neutral.shardling");
    }
}
