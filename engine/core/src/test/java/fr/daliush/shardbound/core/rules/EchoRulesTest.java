package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.chooseTarget;
import static fr.daliush.shardbound.core.scenario.Choices.echoOrder;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.ContentException;
import fr.daliush.shardbound.core.content.json.CardParser;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Rulebook 11.1: Echo, with the trigger rules it takes part in (9.3, 9.10, 8.22). */
class EchoRulesTest {

    @Test
    @DisplayName("11.1.1 — when a unit dies, its attack abilities with Echo are replayed, without paying their cost")
    void replayedOnDeath() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P1, "neutral.shardling").unit(P2, "ember.ash-warden").build(),
                play("neutral.crystal-rupture").on(unit("ember.ash-warden")));

        assertThat(trace(result)).containsSubsequence("UnitDestroyed[8.2]", "SpellResolved[6.3, 3.5]",
                "EchoTriggered[11.1.1, 11.1.2]", "UnitDamaged[8.1]");
        assertThat(result.events(GameEvent.EchoTriggered.class)).singleElement()
                .matches(echo -> echo.attackIndex() == 0, "only Cinder Bite has Echo");
        assertThat(result.unit("neutral.shardling").defense()).isEqualTo(1);
        assertThat(result.player(P2).shards().available()).isZero();
    }

    @Test
    @DisplayName("11.1.2 — an echo multiplies every number by X%, rounded down; X can exceed 100")
    void scaledNumbers() {
        ScenarioResult halved = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P1, "neutral.shard-construct").unit(P2, "ember.ash-warden").build(),
                play("neutral.crystal-rupture").on(unit("ember.ash-warden")));
        ScenarioResult doubled = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P2, "ember.ashborn-drake").build(),
                play("neutral.crystal-rupture").on(unit("ember.ashborn-drake")));

        assertThat(halved.unit("neutral.shard-construct").defense()).as("4 at 50%").isEqualTo(7);
        assertThat(doubled.player(P1).hp()).as("7 at 200%, on the player with no unit (7.3)").isEqualTo(36);
    }

    @Test
    @DisplayName("11.1.3 — the owner picks the echo's new target with the rules of an attack, on their opponent's turn")
    void ownerPicksTheTarget() {
        GameState start = scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture").unit(P1, "neutral.shardling")
                .unit(P1, "ember.cinderling").relic(P1, "root.heartwood-shrine").unit(P2, "ember.ash-warden").build();

        ScenarioResult asked = run(start, play("neutral.crystal-rupture").on(unit("ember.ash-warden")));
        ScenarioResult answered = run(start, play("neutral.crystal-rupture").on(unit("ember.ash-warden")),
                chooseTarget(unit("ember.cinderling")));

        Decision choice = asked.pending().orElseThrow();
        assertThat(choice.player()).isEqualTo(P2);
        assertThat(choice.kind()).isEqualTo(DecisionKind.CHOOSE_TARGET);
        assertThat(choice.actions()).containsExactly(
                new Action.ChooseTarget(TargetRef.unit(start.p1().units().get(0).id())),
                new Action.ChooseTarget(TargetRef.unit(start.p1().units().get(1).id())));
        assertThat(answered.findUnit("ember.cinderling")).isEmpty();
        assertThat(answered.unit("neutral.shardling").defense()).isEqualTo(3);
    }

    @Test
    @DisplayName("11.1.4 — an echo is not an attack: no cost, no intercept, no \"Attack\" ability")
    void notAnAttack() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P1, "neutral.shardling").unit(P1, "neutral.shard-construct").unit(P2, "test.howler").build(),
                play("neutral.crystal-rupture").on(unit("test.howler")),
                chooseTarget(unit("neutral.shardling")));

        assertThat(result.decisions()).extracting(Decision::kind)
                .containsExactly(DecisionKind.MAIN, DecisionKind.CHOOSE_TARGET);
        assertThat(result.pending().orElseThrow().kind()).as("no intercept").isEqualTo(DecisionKind.MAIN);
        assertThat(trace(result)).contains("EchoTriggered[11.1.1, 11.1.2]", "UnitDamaged[8.1]")
                .doesNotContain("AttackDeclared[7.4]", "AbilityTriggered[9.9]");
        assertThat(result.player(P1).hp()).isEqualTo(50);
        assertThat(result.unit("neutral.shardling").defense()).isEqualTo(1);
    }

    @Test
    @DisplayName("11.1.5 — a unit returned to hand does not die, even into a full hand: no echo")
    void noEchoWhenReturned() {
        ScenarioResult toHand = run(scenario().shards(P1, 3).hand(P1, "tide.receding-wave")
                        .unit(P2, "ember.ash-warden").unit(P1, "neutral.shardling").build(),
                play("tide.receding-wave").on(unit("ember.ash-warden")));
        ScenarioResult toGraveyard = run(scenario().shards(P1, 3).hand(P1, "tide.receding-wave")
                        .hand(P2, "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                                "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                                "neutral.shardling", "neutral.shardling")
                        .unit(P2, "ember.ash-warden").unit(P1, "neutral.shardling").build(),
                play("tide.receding-wave").on(unit("ember.ash-warden")));

        assertThat(trace(toHand)).contains("ReturnedToHand[8.8, 6.7]").doesNotContain("EchoTriggered[11.1.1, 11.1.2]");
        assertThat(trace(toGraveyard)).contains("SentToGraveyardHandFull[8.8, 3.3]")
                .doesNotContain("EchoTriggered[11.1.1, 11.1.2]");
    }

    @Test
    @DisplayName("9.3 — a sacrificed unit dies, so its Echo replays once the card it paid for has resolved")
    void sacrificedUnitEchoes() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.pyre-offering")
                        .unit(P1, "ember.ash-warden").unit(P2, "root.thornback-ancient").build(),
                play("ember.pyre-offering").on(unit("root.thornback-ancient")).sacrificing(unit("ember.ash-warden")));

        assertThat(trace(result)).containsSubsequence("UnitSacrificed[6.3, 8.3]", "UnitDamaged[8.1]",
                "SpellResolved[6.3, 3.5]", "EchoTriggered[11.1.1, 11.1.2]", "UnitDamaged[8.1]");
        assertThat(result.unit("root.thornback-ancient").defense()).isEqualTo(5);
    }

    @Test
    @DisplayName("11.1.6 — an echo that kills a unit with Echo triggers that unit's echo in turn")
    void chain() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.spark-dart")
                        .unit(P1, "ember.ash-warden", unit -> unit.defense(2))
                        .unit(P2, "ember.ash-warden", unit -> unit.defense(3)).build(),
                play("ember.spark-dart").on(unit("ember.ash-warden", 2)));

        assertThat(trace(result)).containsSubsequence("UnitDestroyed[6.6]", "EchoTriggered[11.1.1, 11.1.2]",
                "UnitDamaged[8.1]", "UnitDestroyed[6.6]", "EchoTriggered[11.1.1, 11.1.2]", "PlayerDamaged[8.1]");
        assertThat(result.player(P2).hp()).isEqualTo(48);
        assertThat(result.state().unitsByArrival()).isEmpty();
    }

    @Test
    @DisplayName("11.1.7 — in v1, only attack abilities carry Echo: a spell with Echo is not a valid card")
    void onlyOnAttacks() {
        String spellWithEcho = """
                { "id": "test.echoing-bolt", "name": "Echoing Bolt", "faction": "neutral", "type": "spell", "cost": 1,
                  "echo": 50, "effects": [{ "effect": "damage", "amount": 3, "target": "opponent" }] }""";

        assertThatThrownBy(() -> new CardParser().parse(JsonMapper.builder().build().readTree(spellWithEcho), "test"))
                .isInstanceOf(ContentException.class).hasMessageContaining("echo");
    }

    @Test
    @DisplayName("11.1.8 — both attack abilities with Echo replay, each with its own X, in the order the owner picks")
    void twoEchoes() {
        GameState start = scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                .unit(P1, "neutral.shard-construct").unit(P2, "test.twin-wyrm").build();

        ScenarioResult asked = run(start, play("neutral.crystal-rupture").on(unit("test.twin-wyrm")));
        ScenarioResult result = run(start, play("neutral.crystal-rupture").on(unit("test.twin-wyrm")), echoOrder(1, 0));

        Decision order = asked.pending().orElseThrow();
        assertThat(order.player()).isEqualTo(P2);
        assertThat(order.actions()).containsExactly(new Action.ChooseOrder(List.of(0, 1)),
                new Action.ChooseOrder(List.of(1, 0)));
        assertThat(result.events(GameEvent.EchoTriggered.class)).extracting(GameEvent.EchoTriggered::attackIndex)
                .containsExactly(1, 0);
        assertThat(trace(result)).containsSubsequence("EchoTriggered[11.1.1, 11.1.2]", "Frozen[8.10]",
                "UnitDamaged[8.1]", "EchoTriggered[11.1.1, 11.1.2]", "UnitDamaged[8.1]", "UnitDestroyed[6.6]");
        assertThat(result.events(GameEvent.UnitDamaged.class)).extracting(GameEvent.UnitDamaged::amount)
                .as("Freeze applies in full, 10 at 50%, then 4 at 100%").containsExactly(5, 4);
    }

    @Test
    @DisplayName("11.1.9 — an echo replays the attack as printed: buffs on the dead unit are lost")
    void printedValues() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "ember.pyre-offering")
                        .unit(P1, "ember.ash-warden").unit(P2, "root.thornback-ancient").build(),
                attack("ember.ash-warden").withAttack(1),
                play("ember.pyre-offering").on(unit("root.thornback-ancient")).sacrificing(unit("ember.ash-warden")));

        assertThat(trace(result)).contains("Modified[8.5]", "EchoTriggered[11.1.1, 11.1.2]");
        assertThat(result.unit("root.thornback-ancient").defense()).as("15 - 8 - 2, not 15 - 8 - 3").isEqualTo(5);
    }

    @Test
    @DisplayName("11.1.10 — an echo rounds toward 0, maluses too; its \"self\" effects do nothing, \"you\" is its owner")
    void roundedTowardZero() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P1, "neutral.shard-construct").unit(P2, "test.shade")
                        .deck(P2, "neutral.shardling", "neutral.shardling").build(),
                play("neutral.crystal-rupture").on(unit("test.shade")));

        assertThat(result.events(GameEvent.Modified.class)).singleElement()
                .matches(modified -> modified.attackDamage() == -1 && modified.defense() == -1, "-3/-3 at 50%");
        assertThat(result.events(GameEvent.UnitHealed.class)).as("the shade is dead").isEmpty();
        assertThat(result.player(P2).hand()).as("2 cards at 50%, for the shade's owner").hasSize(1);
    }

    @Test
    @DisplayName("9.10 — when a unit dies, its Echo resolves first, then its Death, then its Departure abilities")
    void echoThenDeathThenDeparture() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P1, "neutral.shard-construct").unit(P2, "test.shade")
                        .deck(P2, "neutral.shardling").build(),
                play("neutral.crystal-rupture").on(unit("test.shade")));

        assertThat(trace(result)).containsSubsequence("EchoTriggered[11.1.1, 11.1.2]", "Modified[8.5]",
                "AbilityTriggered[9.3]", "PlayerDamaged[8.1]", "AbilityTriggered[9.4]", "PlayerHealed[8.4]");
    }

    @Test
    @DisplayName("8.22 — an echo whose sacrifices cannot be made does nothing at all")
    void echoWithoutItsSacrifice() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture")
                        .unit(P1, "neutral.shardling").unit(P2, "test.bloodfang").build(),
                play("neutral.crystal-rupture").on(unit("test.bloodfang")));

        assertThat(trace(result)).containsSubsequence("EchoTriggered[11.1.1, 11.1.2]", "SacrificeFailed[8.22]")
                .doesNotContain("UnitDamaged[8.1]");
        assertThat(result.unit("neutral.shardling").defense()).isEqualTo(3);
    }
}
