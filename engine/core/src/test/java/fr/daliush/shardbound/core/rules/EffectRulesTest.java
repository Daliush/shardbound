package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.player;
import static fr.daliush.shardbound.core.scenario.Pick.relic;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 8, the effects of this slice: Damage, Destroy, Heal, Draw, Summon. */
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
}
