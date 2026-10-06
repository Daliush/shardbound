package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
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
    @DisplayName("6.5 — a unit arrives with its current defense equal to its max defense")
    void arrivesAtFullDefense() {
        ScenarioResult result = run(scenario().shards(P1, 4).hand(P1, "neutral.shard-construct").build(),
                play("neutral.shard-construct"));

        Unit construct = result.unit("neutral.shard-construct");
        assertThat(construct.defense()).isEqualTo(9);
        assertThat(construct.maxDefense()).isEqualTo(9);
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
    @DisplayName("6.8 — with no cost change, a card costs its printed cost")
    void printedCost() {
        ScenarioResult result = run(scenario().shards(P1, 7).hand(P1, "root.thornback-ancient").build(),
                play("root.thornback-ancient"));

        assertThat(result.events(GameEvent.CardPlayed.class).getFirst().cost()).isEqualTo(7);
        assertThat(result.player(P1).shards().available()).isZero();
    }

    @Test
    @DisplayName("6.9 — damage beyond a unit's defense is lost: defense never goes below 0")
    void defenseFloor() {
        Unit sprout = Unit.arriving(new CardInstance(InstanceId.of(1), new CardId("root.sprout"), PlayerId.P1),
                PlayerId.P1, true, 2, 1, 1);

        assertThat(sprout.damaged(5).defense()).isZero();
    }
}
