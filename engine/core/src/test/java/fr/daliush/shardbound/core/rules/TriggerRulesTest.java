package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.relic;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 9: triggers and the order of simultaneous abilities. */
class TriggerRulesTest {

    @Test
    @DisplayName("9.2 — \"Arrival\" abilities trigger when the card arrives on the board")
    void arrival() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "root.sproutcaller").build(),
                play("root.sproutcaller"));

        assertThat(triggered(result)).containsExactly(Trigger.ARRIVAL);
        assertThat(result.player(P1).units()).hasSize(2);
    }

    @Test
    @DisplayName("9.3 — \"Death\" abilities trigger when the unit dies")
    void death() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.spark-dart")
                        .unit(P2, "ember.cinderling").build(),
                play("ember.spark-dart").on(unit("ember.cinderling")));

        assertThat(trace(result)).containsSubsequence("UnitDestroyed[6.6]", "AbilityTriggered[9.3]",
                "PlayerDamaged[8.1]");
        assertThat(result.player(P1).hp()).isEqualTo(48);
    }

    @Test
    @DisplayName("9.4 — \"Departure\" abilities trigger when the card leaves the board")
    void departure() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P2, "test.guard").deck(P2, "neutral.shardling").build(),
                play("neutral.crystal-rupture").on(unit("test.guard")));

        assertThat(triggered(result)).contains(Trigger.DEPARTURE);
        assertThat(result.player(P2).hand()).hasSize(1);
    }

    @Test
    @DisplayName("9.5 — \"Turn start\" abilities trigger at the start of their controller's turn")
    void turnStart() {
        ScenarioResult result = run(scenario().relic(P2, "root.heartwood-shrine").build(), endTurn());

        assertThat(triggered(result)).containsExactly(Trigger.TURN_START);
        assertThat(result.player(P2).units()).hasSize(1);
    }

    @Test
    @DisplayName("9.7 — a continuous ability applies only while its card is on the board")
    void continuous() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.uproot").relic(P2, "tide.coral-font")
                        .unit(P2, "neutral.shardling").build(),
                play("test.uproot").on(relic("tide.coral-font")));

        assertThat(trace(result)).containsSubsequence("AuraApplied[8.14]", "ReturnedToHand[8.8, 6.7]",
                "AuraRemoved[8.14]");
        assertThat(result.unit("neutral.shardling").maxDefense()).isEqualTo(3);
    }

    @Test
    @DisplayName("9.6 — \"Turn end\" abilities trigger at the end of their controller's turn only")
    void turnEnd() {
        ScenarioResult result = run(scenario().hp(P1, 40).hp(P2, 40).deck(P2, "neutral.shardling")
                        .unit(P1, "test.guard").unit(P2, "test.guard").build(),
                endTurn());

        assertThat(result.player(P1).hp()).isEqualTo(42);
        assertThat(result.player(P2).hp()).isEqualTo(40);
    }

    @Test
    @DisplayName("9.8 — simultaneous abilities: the active player's first, then the earliest arrival first")
    void simultaneousOrder() {
        ScenarioResult result = run(scenario().shards(P1, 7).hand(P1, "neutral.tempest")
                        .unit(P2, "ember.cinderling").unit(P2, "ember.cinderling")
                        .unit(P1, "ember.cinderling").build(),
                play("neutral.tempest"));

        assertThat(result.events(GameEvent.AbilityTriggered.class))
                .extracting(event -> event.source().id().value())
                .containsExactly(4, 2, 3);
        assertThat(result.events(GameEvent.PlayerDamaged.class)).extracting(GameEvent.PlayerDamaged::player)
                .containsExactly(PlayerId.P2, PlayerId.P1, PlayerId.P1);
    }

    @Test
    @DisplayName("9.9 — \"Attack\" abilities resolve once the attack is declared, before the intercept decision")
    void attackAbilities() {
        ScenarioResult result = run(scenario().shards(P1, 1).unit(P1, "test.charger")
                        .unit(P2, "neutral.shard-construct").unit(P2, "neutral.shardling").build(),
                attack("test.charger").on(unit("neutral.shard-construct")));

        assertThat(trace(result)).containsSubsequence("AttackDeclared[7.4]", "AbilityTriggered[9.9]",
                "UnitDamaged[8.1]", "UnitDamaged[8.1]", "UnitDestroyed[6.6]", "UnitDamaged[8.1]");
        assertThat(result.decisions()).hasSize(1);
        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(4);
    }

    @Test
    @DisplayName("9.10 — for one card, \"Death\" abilities resolve before \"Departure\" abilities")
    void deathBeforeDeparture() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P2, "test.guard").deck(P2, "neutral.shardling").build(),
                play("neutral.crystal-rupture").on(unit("test.guard")));

        assertThat(triggered(result)).containsExactly(Trigger.DEATH, Trigger.DEPARTURE);
    }

    @Test
    @DisplayName("9.11 — an action resolves completely before the abilities it triggered")
    void actionBeforeTriggers() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "ember.cinderfall")
                        .unit(P2, "ember.cinderling").build(),
                play("ember.cinderfall"));

        assertThat(trace(result)).containsSubsequence("UnitDestroyed[6.6]", "SpellResolved[6.3, 3.5]",
                "AbilityTriggered[9.3]", "PlayerDamaged[8.1]");
    }

    private static List<Trigger> triggered(ScenarioResult result) {
        return result.events(GameEvent.AbilityTriggered.class).stream().map(GameEvent.AbilityTriggered::trigger)
                .toList();
    }
}
