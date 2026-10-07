package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Choices.sacrifice;
import static fr.daliush.shardbound.core.scenario.Pick.player;
import static fr.daliush.shardbound.core.scenario.Pick.relic;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 8: the effects. */
class EffectRulesTest {

    @Test
    @DisplayName("8.1 — damage removes defense from a unit, or HP from a player")
    void damage() {
        ScenarioResult onUnit = run(scenario().shards(P1, 1).hand(P1, "ember.spark-dart")
                        .unit(P2, "neutral.shard-construct").build(),
                play("ember.spark-dart").on(unit("neutral.shard-construct")));
        ScenarioResult onPlayer = run(scenario().shards(P1, 1).unit(P1, "neutral.shardling").build(),
                attack("neutral.shardling").on(player(P2)));

        assertThat(onUnit.unit("neutral.shard-construct").defense()).isEqualTo(6);
        assertThat(onPlayer.player(P2).hp()).isEqualTo(47);
        assertThat(trace(onPlayer)).contains("PlayerDamaged[8.1]");
    }

    @Test
    @DisplayName("8.2 — destroy sends a unit to the graveyard whatever its defense")
    void destroyUnit() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P2, "root.thornback-ancient").build(),
                play("neutral.crystal-rupture").on(unit("root.thornback-ancient")));

        assertThat(result.player(P2).units()).isEmpty();
        assertThat(result.player(P2).graveyard()).hasSize(1);
        assertThat(trace(result)).contains("UnitDestroyed[8.2]");
    }

    @Test
    @DisplayName("8.2 — destroy also sends a relic to the graveyard")
    void destroyRelic() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.shatter")
                        .relic(P2, "root.heartwood-shrine").build(),
                play("test.shatter").on(relic("root.heartwood-shrine")));

        assertThat(result.player(P2).relics()).isEmpty();
        assertThat(result.player(P2).graveyard()).hasSize(1);
        assertThat(trace(result)).contains("RelicDestroyed[8.2]");
    }

    @Test
    @DisplayName("8.3 — a Sacrifice effect: its controller chooses which of their units die")
    void sacrificeEffect() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.ritual")
                        .unit(P1, "ember.cinderling").unit(P1, "neutral.shardling")
                        .deck(P1, "neutral.shardling", "neutral.shardling").build(),
                play("test.ritual"), sacrifice(unit("neutral.shardling")));

        assertThat(result.decisions()).extracting(Decision::kind)
                .containsExactly(DecisionKind.MAIN, DecisionKind.CHOOSE_CARDS);
        assertThat(result.player(P1).units()).extracting(Unit::card).containsExactly(new CardId("ember.cinderling"));
        assertThat(result.player(P1).hand()).hasSize(2);
        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3]", "UnitSacrificed[8.3]", "CardDrawn[8.6]",
                "CardDrawn[8.6]", "SpellResolved[6.3, 3.5]");
    }

    @Test
    @DisplayName("8.16 — with exactly the units a Sacrifice effect asks for, they are sacrificed without a choice")
    void sacrificeWithoutAChoice() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.ritual").unit(P1, "ember.cinderling")
                        .deck(P1, "neutral.shardling", "neutral.shardling").build(),
                play("test.ritual"));

        assertThat(result.pending().orElseThrow().kind()).isEqualTo(DecisionKind.MAIN);
        assertThat(result.player(P1).units()).isEmpty();
        assertThat(trace(result)).containsSubsequence("UnitSacrificed[8.3]", "SpellResolved[6.3, 3.5]",
                "AbilityTriggered[9.3]", "PlayerDamaged[8.1]");
    }

    @Test
    @DisplayName("8.16 — a card or an attack whose sacrifices cannot all be made cannot be played or used")
    void sacrificesAreNeverPartial() {
        GameState noUnit = scenario().shards(P1, 5)
                .hand(P1, "ember.pyre-offering", "ember.flamebound-zealot", "test.ritual")
                .unit(P2, "neutral.shardling").build();
        GameState knightAlone = scenario().shards(P1, 1).unit(P1, "test.blood-knight")
                .unit(P2, "neutral.shardling").build();
        GameState knightAndAnotherUnit = scenario().shards(P1, 1).unit(P1, "test.blood-knight")
                .unit(P1, "neutral.shardling").unit(P2, "neutral.shardling").build();

        assertThat(actions(noUnit)).containsExactly(new Action.EndTurn());
        assertThat(actions(knightAlone)).containsExactly(new Action.EndTurn());
        assertThat(actions(knightAndAnotherUnit)).contains(
                new Action.Attack(InstanceId.of(1), 0, Optional.of(TargetRef.unit(InstanceId.of(3)))));
    }

    @Test
    @DisplayName("8.22 — a triggered ability whose sacrifices cannot all be made does nothing at all")
    void abilityWithoutItsSacrifices() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.altar").build(), play("test.altar"));

        assertThat(trace(result)).containsSubsequence("UnitArrived[6.3, 6.5]", "AbilityTriggered[9.2]",
                "SacrificeFailed[8.22]");
        assertThat(result.events(GameEvent.UnitSacrificed.class)).isEmpty();
        assertThat(result.unit("test.altar").defense()).isEqualTo(3);
        assertThat(result.player(P2).hp()).isEqualTo(50);
    }

    @Test
    @DisplayName("8.22 — a sacrifice that can no longer be made stops its effects; those already applied stay")
    void sacrificeNoLongerPossible() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.cataclysm")
                        .unit(P1, "neutral.shardling").unit(P2, "neutral.shardling")
                        .deck(P1, "neutral.shardling", "neutral.shardling").build(),
                play("test.cataclysm"));

        assertThat(trace(result)).containsSubsequence("UnitDamaged[8.1]", "UnitDamaged[8.1]", "UnitDestroyed[6.6]",
                "UnitDestroyed[6.6]", "SacrificeFailed[8.22]", "SpellResolved[6.3, 3.5]");
        assertThat(result.events(GameEvent.CardDrawn.class)).isEmpty();
        assertThat(result.player(P1).deck()).hasSize(2);
    }

    @Test
    @DisplayName("8.4 — heal restores defense up to the max, and HP up to 50")
    void heal() {
        ScenarioResult onUnits = run(scenario().shards(P1, 1)
                        .unit(P1, "root.mossmender", unit -> unit.defense(4))
                        .unit(P1, "neutral.shardling", unit -> unit.defense(1)).build(),
                attack("root.mossmender").withAttack(1));
        ScenarioResult onPlayer = run(scenario().hp(P1, 48).shards(P1, 2).hand(P1, "root.renewing-sap").build(),
                play("root.renewing-sap"));

        assertThat(onUnits.unit("root.mossmender").defense()).isEqualTo(5);
        assertThat(onUnits.unit("neutral.shardling").defense()).isEqualTo(3);
        assertThat(onPlayer.player(P1).hp()).isEqualTo(50);
        assertThat(trace(onPlayer)).contains("PlayerHealed[8.4]");
    }

    @Test
    @DisplayName("8.6 — draw takes cards one at a time: fatigue applies to each one")
    void draw() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.scholar")
                        .deck(P1, "neutral.shardling").build(),
                play("test.scholar"));

        assertThat(trace(result)).containsSubsequence("CardDrawn[8.6]", "HpLost[8.6, 1.4]");
        assertThat(result.player(P1).hand()).hasSize(1);
        assertThat(result.player(P1).hp()).isEqualTo(49);
    }

    @Test
    @DisplayName("8.9 — summon creates tokens on its controller's board")
    void summon() {
        ScenarioResult result = run(scenario().shards(P1, 3).hand(P1, "root.verdant-burst").build(),
                play("root.verdant-burst"));

        assertThat(result.player(P1).units()).hasSize(3).allMatch(Unit::token)
                .allMatch(unit -> unit.arrivedTurn() == result.state().turn());
        assertThat(result.events(GameEvent.TokenSummoned.class)).hasSize(3);
    }

    private static List<Action> actions(GameState start) {
        return ENGINE.resume(start).state().pending().orElseThrow().actions();
    }
}
