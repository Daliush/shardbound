package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.Shards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook 11.4: Overcharge, and the cost rule it takes part in (6.8). */
class OverchargeRulesTest {

    @Test
    @DisplayName("11.4.1 — a card with Overcharge is also offered overcharged, for 2 Shards less")
    void offeredOvercharged() {
        GameState start = scenario().shards(P1, 4).hand(P1, "ember.ember-lance").build();

        Decision main = ENGINE.resume(start).state().pending().orElseThrow();
        ScenarioResult result = run(start, play("ember.ember-lance").overcharged());

        assertThat(main.actions()).filteredOn(Action.PlayCard.class::isInstance)
                .extracting(action -> ((Action.PlayCard) action).overcharge()).containsExactly(false, true);
        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3, 11.4.1]", "ShardsLocked[11.4.2, 11.4.3]",
                "PlayerDamaged[8.1]");
        assertThat(result.events(GameEvent.CardPlayed.class).getFirst().cost()).isEqualTo(2);
        assertThat(result.player(P1).shards().available()).isEqualTo(2);
    }

    @Test
    @DisplayName("11.4.1 — overcharging never takes a cost below 0, and can make a card affordable")
    void neverBelowZero() {
        GameState start = scenario().hand(P1, "ember.blaze-mastiff").build();

        Decision main = ENGINE.resume(start).state().pending().orElseThrow();
        ScenarioResult result = run(start, play("ember.blaze-mastiff").overcharged());

        assertThat(main.actions()).filteredOn(Action.PlayCard.class::isInstance).singleElement()
                .matches(action -> ((Action.PlayCard) action).overcharge());
        assertThat(result.events(GameEvent.CardPlayed.class).getFirst().cost()).isZero();
        assertThat(result.player(P1).units()).hasSize(1);
    }

    @Test
    @DisplayName("6.8 — overcharged under a cost aura: 1 + 1 − 2 = 0 Shards, and the card still locks 2")
    void costAuraAndOvercharge() {
        ScenarioResult result = run(scenario().hand(P1, "test.flare").relic(P2, "test.tithe").build(),
                play("test.flare").overcharged());

        assertThat(result.events(GameEvent.CardPlayed.class).getFirst().cost()).isZero();
        assertThat(result.player(P1).shards().lockedNextTurn()).isEqualTo(2);
    }

    @Test
    @DisplayName("11.4.2 — on the player's next turn, 2 of their Shards are locked; their max still grows")
    void locksNextTurn() {
        ScenarioResult result = run(scenario().shards(P1, 5).hand(P1, "ember.ember-lance")
                        .deck(P1, "neutral.shardling").deck(P2, "neutral.shardling").build(),
                play("ember.ember-lance").overcharged(), endTurn(), endTurn());

        assertThat(trace(result)).contains("ShardsRefilled[4.1, 4.2, 11.4.2]");
        assertThat(result.player(P1).shards()).isEqualTo(new Shards(6, 4, 0));
    }

    @Test
    @DisplayName("11.4.3 — overcharges stack: each overcharged card locks 2 more Shards")
    void overchargesStack() {
        ScenarioResult result = run(scenario().shards(P1, 3).hand(P1, "test.flare", "ember.ember-lance").build(),
                play("test.flare").overcharged(), play("ember.ember-lance").overcharged());

        assertThat(result.events(GameEvent.ShardsLocked.class)).extracting(GameEvent.ShardsLocked::total)
                .containsExactly(2, 4);
        assertThat(result.player(P1).shards().lockedNextTurn()).isEqualTo(4);
    }

    @Test
    @DisplayName("11.4.4 — locks beyond the refill leave 0 Shards; the excess is lost, not carried over")
    void excessLost() {
        ScenarioResult result = run(scenario().turn(4).active(P2).maxShards(P1, 2).lockedShards(P1, 4)
                        .deck(P1, "neutral.shardling").build(),
                endTurn());

        assertThat(trace(result)).contains("ShardsRefilled[4.1, 4.2, 11.4.2, 11.4.4]");
        assertThat(result.player(P1).shards()).isEqualTo(new Shards(3, 0, 0));
    }

    @Test
    @DisplayName("11.4.5 — Overcharge never lowers the cost of an attack ability")
    void notOnAttacks() {
        GameState oneShard = scenario().shards(P1, 1).unit(P1, "ember.blaze-mastiff").unit(P2, "root.sprout").build();
        GameState twoShards = scenario().shards(P1, 2).unit(P1, "ember.blaze-mastiff").unit(P2, "root.sprout").build();

        assertThat(ENGINE.resume(oneShard).state().pending().orElseThrow().actions())
                .noneMatch(Action.Attack.class::isInstance);
        assertThat(ENGINE.resume(twoShards).state().pending().orElseThrow().actions())
                .anyMatch(Action.Attack.class::isInstance);
    }
}
