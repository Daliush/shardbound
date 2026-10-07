package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 6: playing cards, defense. */
class CardRulesTest {

    @Test
    @DisplayName("6.3 — playing a unit pays its cost; it arrives and its \"Arrival\" abilities trigger")
    void playUnit() {
        ScenarioResult result = run(scenario().shards(P1, 3).hand(P1, "root.sproutcaller").build(),
                play("root.sproutcaller"));

        assertThat(result.player(P1).shards().available()).isEqualTo(2);
        assertThat(trace(result)).containsSubsequence(
                "CardPlayed[6.3]", "UnitArrived[6.3, 6.5]", "AbilityTriggered[9.2]", "TokenSummoned[8.9]");
        assertThat(result.player(P1).units()).extracting(unit -> unit.card().value())
                .containsExactly("root.sproutcaller", "root.sprout");
    }

    @Test
    @DisplayName("6.3 — a spell applies its effect, then goes to the graveyard")
    void playSpell() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.spark-dart")
                        .unit(P2, "neutral.shard-construct").build(),
                play("ember.spark-dart").on(unit("neutral.shard-construct")));

        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3]", "UnitDamaged[8.1]", "SpellResolved[6.3, 3.5]");
        assertThat(result.player(P1).graveyard()).extracting(CardInstance::card)
                .containsExactly(new CardId("ember.spark-dart"));
        assertThat(result.player(P1).hand()).isEmpty();
    }

    @Test
    @DisplayName("6.3 — a sacrifice cost is paid on play; the abilities it triggers wait for the card")
    void sacrificeCost() {
        GameState start = scenario().shards(P1, 1).hand(P1, "ember.pyre-offering")
                .unit(P1, "ember.cinderling").unit(P1, "neutral.shardling").unit(P2, "neutral.shard-construct").build();

        ScenarioResult result = run(start,
                play("ember.pyre-offering").on(unit("neutral.shard-construct")).sacrificing(unit("ember.cinderling")));

        assertThat(result.decisions().getFirst().actions()).filteredOn(Action.PlayCard.class::isInstance)
                .extracting(action -> ((Action.PlayCard) action).sacrificed())
                .containsExactly(List.of(InstanceId.of(2)), List.of(InstanceId.of(3)));
        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3]", "UnitSacrificed[6.3, 8.3]",
                "UnitDamaged[8.1]", "SpellResolved[6.3, 3.5]", "AbilityTriggered[9.3]", "PlayerDamaged[8.1]");
        assertThat(result.player(P1).units()).extracting(Unit::card).containsExactly(new CardId("neutral.shardling"));
        assertThat(result.player(P1).graveyard()).extracting(CardInstance::card)
                .containsExactly(new CardId("ember.cinderling"), new CardId("ember.pyre-offering"));
        assertThat(result.player(P2).hp()).isEqualTo(48);
    }

    @Test
    @DisplayName("6.5 — a unit arrives with its current defense equal to its max defense")
    void arrivesAtFullDefense() {
        ScenarioResult result = run(scenario().shards(P1, 4).hand(P1, "neutral.shard-construct").build(),
                play("neutral.shard-construct"));

        Unit construct = result.unit("neutral.shard-construct");
        assertThat(construct.defense()).isEqualTo(9);
        assertThat(construct.maxDefense()).isEqualTo(9);
    }

    @Test
    @DisplayName("6.5 — a unit arriving under a stat aura arrives with its bonus, current defense equal to max")
    void arrivesUnderAnAura() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "neutral.shardling")
                        .relic(P1, "tide.coral-font").build(),
                play("neutral.shardling"));

        Unit shardling = result.unit("neutral.shardling");
        assertThat(shardling.defense()).isEqualTo(5);
        assertThat(shardling.maxDefense()).isEqualTo(5);
        assertThat(trace(result)).containsSubsequence("UnitArrived[6.3, 6.5]", "AuraApplied[8.14]");
    }

    @Test
    @DisplayName("6.6 — a unit whose defense drops to 0 is destroyed at once")
    void zeroDefenseDestroys() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.spark-dart")
                        .unit(P2, "neutral.shardling").build(),
                play("ember.spark-dart").on(unit("neutral.shardling")));

        assertThat(result.findUnit("neutral.shardling")).isEmpty();
        assertThat(trace(result)).containsSubsequence("UnitDamaged[8.1]", "UnitDestroyed[6.6]");
        assertThat(result.player(P2).graveyard()).extracting(CardInstance::card)
                .containsExactly(new CardId("neutral.shardling"));
    }

    @Test
    @DisplayName("6.7 — a card returned to hand loses all its modifications and all its damage")
    void returnedCardIsReset() {
        ScenarioResult result = run(scenario().shards(P1, 3).hand(P1, "test.bulwark", "test.flood")
                        .unit(P1, "neutral.shardling", unit -> unit.defense(1)).build(),
                play("test.bulwark").on(unit("neutral.shardling")),
                play("test.flood"),
                play("neutral.shardling"));

        Unit shardling = result.unit("neutral.shardling");
        assertThat(shardling.id()).isEqualTo(InstanceId.of(3));
        assertThat(shardling.defense()).isEqualTo(3);
        assertThat(shardling.maxDefense()).isEqualTo(3);
        assertThat(shardling.modifiers()).isEmpty();
    }

    @Test
    @DisplayName("6.8 — with no cost change, a card costs its printed cost")
    void printedCost() {
        ScenarioResult result = run(scenario().shards(P1, 7).hand(P1, "root.thornback-ancient").build(),
                play("root.thornback-ancient"));

        assertThat(result.events(GameEvent.CardPlayed.class).getFirst().cost()).isEqualTo(7);
        assertThat(result.player(P1).shards().available()).isZero();
    }

    @Test
    @DisplayName("6.8 — a card's cost adds up its printed cost and every cost aura, then is floored at 0 once")
    void costsAddUpThenFloor() {
        ScenarioResult result = run(scenario().hand(P1, "ember.cinderling")
                        .relic(P1, "test.forge").relic(P1, "test.forge").relic(P2, "test.tithe").build(),
                play("ember.cinderling"));

        // 1 - 1 - 1 + 1 = 0. Flooring each step would give 1 - 1 = 0, 0 - 1 floored to 0, then 0 + 1 = 1.
        assertThat(result.events(GameEvent.CardPlayed.class).getFirst().cost()).isZero();
    }

    @Test
    @DisplayName("6.9 — damage beyond a unit's defense is lost: defense never goes below 0")
    void defenseFloor() {
        Unit sprout = Unit.arriving(new CardInstance(InstanceId.of(1), new CardId("root.sprout"), PlayerId.P1),
                PlayerId.P1, true, 2, 1, 1);

        assertThat(sprout.damaged(5).defense()).isZero();
    }
}
