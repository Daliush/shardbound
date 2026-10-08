package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.declineIntercept;
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
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook 11.5 and 8.11: Link. */
class LinkRulesTest {

    @Test
    @DisplayName("8.11 — Link links any two units, allied or enemy, one from each side included")
    void linksAnyTwoUnits() {
        GameState start = scenario().shards(P1, 1).hand(P1, "neutral.binding-thread").unit(P1, "neutral.shardling")
                .unit(P2, "root.sprout").unit(P2, "neutral.shard-construct").build();
        InstanceId shardling = start.p1().units().getFirst().id();
        InstanceId sprout = start.p2().units().get(0).id();
        InstanceId construct = start.p2().units().get(1).id();

        List<List<TargetRef>> pairs = plays(start);
        ScenarioResult result = run(start, play("neutral.binding-thread")
                .on(unit("neutral.shardling"), unit("root.sprout")));

        assertThat(pairs).containsExactly(
                List.of(TargetRef.unit(shardling), TargetRef.unit(sprout)),
                List.of(TargetRef.unit(shardling), TargetRef.unit(construct)),
                List.of(TargetRef.unit(sprout), TargetRef.unit(construct)));
        assertThat(result.unit("neutral.shardling").linkedTo()).contains(sprout);
        assertThat(result.unit("root.sprout").linkedTo()).contains(shardling);
        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3]", "Linked[8.11, 11.5.1]",
                "SpellResolved[6.3, 3.5]");
    }

    @Test
    @DisplayName("11.5.1 — a linked unit cannot be linked again; with fewer than two other units, Link does nothing")
    void oneLinkPerUnit() {
        GameState threeUnits = scenario().shards(P1, 1).hand(P1, "neutral.binding-thread")
                .unit(P1, "neutral.shardling").unit(P2, "root.sprout").unit(P2, "neutral.shard-construct")
                .link("neutral.shardling", "root.sprout").build();

        ScenarioResult result = run(threeUnits, play("neutral.binding-thread"));

        assertThat(plays(threeUnits)).containsExactly(List.of());
        assertThat(trace(result)).doesNotContain("Linked[8.11, 11.5.1]");
        assertThat(result.unit("neutral.shard-construct").linkedTo()).isEmpty();
    }

    @Test
    @DisplayName("11.5.2 — damage to a linked unit is shared: half rounded up for it, the rest for its partner")
    void damageShared() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.spark-dart")
                        .unit(P1, "neutral.shardling").unit(P2, "neutral.shard-construct")
                        .link("neutral.shardling", "neutral.shard-construct").build(),
                play("ember.spark-dart").on(unit("neutral.shard-construct")));

        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(7);
        assertThat(result.unit("neutral.shardling").defense()).as("an allied partner shares too").isEqualTo(2);
        assertThat(trace(result)).containsSubsequence("UnitDamaged[8.1, 11.5.2]", "DamageShared[8.1, 11.5.2]");
    }

    @Test
    @DisplayName("11.5.2 — the transferred share is not shared again, and each hit is shared on its own")
    void shareNotSharedAgain() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "ember.cinderfall")
                        .unit(P2, "neutral.shard-construct").unit(P2, "root.thornback-ancient")
                        .link("neutral.shard-construct", "root.thornback-ancient").build(),
                play("ember.cinderfall"));

        assertThat(result.events(GameEvent.UnitDamaged.class)).extracting(GameEvent.UnitDamaged::amount)
                .containsExactly(2, 2);
        assertThat(result.events(GameEvent.DamageShared.class)).extracting(GameEvent.DamageShared::amount)
                .containsExactly(2, 2);
        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(5);
        assertThat(result.unit("root.thornback-ancient").defense()).isEqualTo(11);
    }

    @Test
    @DisplayName("11.5.3 — only damage is shared: destroy, debuffs, freeze and return to hand are not")
    void onlyDamage() {
        GameState start = scenario().shards(P1, 7).hand(P1, "test.hex", "test.frost", "neutral.crystal-rupture")
                .unit(P2, "neutral.shard-construct").unit(P2, "root.thornback-ancient")
                .link("neutral.shard-construct", "root.thornback-ancient").build();

        ScenarioResult result = run(start, play("test.hex").on(unit("neutral.shard-construct")),
                play("test.frost").on(unit("neutral.shard-construct")),
                play("neutral.crystal-rupture").on(unit("neutral.shard-construct")));

        Unit ancient = result.unit("root.thornback-ancient");
        assertThat(ancient.defense()).isEqualTo(15);
        assertThat(ancient.modifiers()).isEmpty();
        assertThat(ancient.frozenThroughTurn()).isZero();
        assertThat(result.player(P2).units()).containsExactly(ancient);
    }

    @Test
    @DisplayName("11.5.3 — Tempest on an anchored unit linked to another: the design doc's Arbiter example")
    void arbiterExample() {
        ScenarioResult result = run(scenario().turn(4).active(P2).shards(P2, 7).hand(P2, "neutral.tempest")
                        .unit(P1, "root.root-sentinel", unit -> unit.anchorProtected()).unit(P1, "root.bramble-warden")
                        .link("root.root-sentinel", "root.bramble-warden").build(),
                play("neutral.tempest"));

        assertThat(trace(result)).containsSubsequence("AnchorPrevented[11.3.2, 11.3.3]", "UnitDestroyed[8.2]",
                "LinkBroken[11.5.4]");
        assertThat(result.player(P1).units()).extracting(Unit::card).extracting(Object::toString)
                .containsExactly("root.root-sentinel");
        assertThat(result.unit("root.root-sentinel").linkedTo()).isEmpty();
    }

    @Test
    @DisplayName("11.5.4 — the link breaks when either unit leaves the board, by dying or by returning to hand")
    void linkBreaks() {
        GameState start = scenario().shards(P1, 3).hand(P1, "tide.receding-wave").unit(P1, "neutral.shardling")
                .unit(P2, "neutral.shard-construct").unit(P2, "root.sprout")
                .link("neutral.shard-construct", "root.sprout").build();

        ScenarioResult returned = run(start, play("tide.receding-wave").on(unit("neutral.shard-construct")));
        ScenarioResult died = run(start, attack("neutral.shardling").on(unit("root.sprout")), declineIntercept());

        assertThat(trace(returned)).containsSubsequence("ReturnedToHand[8.8, 6.7]", "LinkBroken[11.5.4]");
        assertThat(returned.unit("root.sprout").linkedTo()).isEmpty();
        assertThat(trace(died)).containsSubsequence("UnitDestroyed[6.6]", "LinkBroken[11.5.4]");
        assertThat(died.unit("neutral.shard-construct").linkedTo()).isEmpty();
        assertThat(died.unit("neutral.shard-construct").defense()).as("3 damage: 2 for the Sprout, 1 shared")
                .isEqualTo(8);
    }

    /** The targets of every play of the card in hand. */
    private static List<List<TargetRef>> plays(GameState start) {
        return ENGINE.resume(start).state().pending().orElseThrow().actions().stream()
                .filter(Action.PlayCard.class::isInstance)
                .map(action -> ((Action.PlayCard) action).targets())
                .toList();
    }
}
