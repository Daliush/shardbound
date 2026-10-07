package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.declineIntercept;
import static fr.daliush.shardbound.core.scenario.Choices.discard;
import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Choices.interceptWith;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Choices.sacrifice;
import static fr.daliush.shardbound.core.scenario.Pick.graveyardCard;
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
import static org.assertj.core.api.Assertions.tuple;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Pick;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.Shards;
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
    @DisplayName("8.5 — Modify adds X to the damage of each damaging attack, and Y to both max and current defense")
    void modify() {
        ScenarioResult result = run(scenario().shards(P1, 4).hand(P1, "ember.roaring-pyre", "test.bulwark")
                        .unit(P1, "ember.cinderling").build(),
                play("ember.roaring-pyre"), play("test.bulwark").on(unit("ember.cinderling")),
                attack("ember.cinderling").on(player(P2)));

        Unit cinderling = result.unit("ember.cinderling");
        assertThat(cinderling.defense()).isEqualTo(5);
        assertThat(cinderling.maxDefense()).isEqualTo(5);
        assertThat(result.player(P2).hp()).isEqualTo(44);
        assertThat(trace(result)).containsSubsequence("Modified[8.5]", "Modified[8.5]", "PlayerDamaged[8.1]");
    }

    @Test
    @DisplayName("8.5 — a debuff that brings defense to 0 destroys the unit")
    void debuffKills() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.hex")
                        .unit(P2, "ember.cinderling").build(),
                play("test.hex").on(unit("ember.cinderling")));

        assertThat(result.player(P2).units()).isEmpty();
        assertThat(trace(result)).containsSubsequence("Modified[8.5]", "UnitDestroyed[6.6]");
    }

    @Test
    @DisplayName("8.5 — an expiring bonus lowers max defense; current defense only drops to the new max")
    void expiringBonus() {
        ScenarioResult result = run(scenario().shards(P1, 3).hand(P1, "test.ward", "test.ward", "ember.spark-dart")
                        .unit(P2, "neutral.shardling").unit(P2, "neutral.shardling").deck(P2, "neutral.shardling")
                        .build(),
                play("test.ward").on(unit("neutral.shardling", 1)),
                play("test.ward").on(unit("neutral.shardling", 2)),
                play("ember.spark-dart").on(unit("neutral.shardling", 2)),
                endTurn());

        // The rulebook's example: 3/3 gets +0/+2 (5/5); untouched it ends at 3/3, after 3 damage (2/5) at 2/3.
        assertThat(result.player(P2).units()).extracting(Unit::defense, Unit::maxDefense)
                .containsExactly(tuple(3, 3), tuple(2, 3));
        assertThat(trace(result)).containsSubsequence("ModifierExpired[5.4.2, 8.5]", "ModifierExpired[5.4.2, 8.5]",
                "TurnEnded[5.4]");
    }

    @Test
    @DisplayName("8.17 — an expiring malus gives back what it took: max and current defense both go back up")
    void expiringMalus() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "test.hex", "ember.spark-dart")
                        .unit(P2, "neutral.shard-construct").deck(P2, "neutral.shardling").build(),
                play("test.hex").on(unit("neutral.shard-construct")),
                play("ember.spark-dart").on(unit("neutral.shard-construct")),
                endTurn());

        // 9/9, then -0/-2 (7/7), then 3 damage (4/7): the malus ends and gives its 2 back (6/9).
        Unit construct = result.unit("neutral.shard-construct");
        assertThat(construct.defense()).isEqualTo(6);
        assertThat(construct.maxDefense()).isEqualTo(9);
        assertThat(construct.modifiers()).isEmpty();
        assertThat(trace(result)).contains("ModifierExpired[5.4.2, 8.17]");
    }

    @Test
    @DisplayName("8.18 — an attack damage bonus applies to the unit's attacks, not to its other abilities")
    void attackBonusOnlyOnAttacks() {
        ScenarioResult result = run(scenario().shards(P1, 4).hand(P1, "ember.roaring-pyre", "test.cataclysm")
                        .unit(P1, "ember.cinderling").build(),
                play("ember.roaring-pyre"),
                attack("ember.cinderling").on(player(P2)),
                play("test.cataclysm"));

        // Flick deals 3 + 3; then Cinderling dies and its "Death" ability deals its printed 2.
        assertThat(result.events(GameEvent.PlayerDamaged.class)).extracting(GameEvent.PlayerDamaged::amount)
                .containsExactly(6, 2);
        assertThat(result.player(P2).hp()).isEqualTo(42);
    }

    @Test
    @DisplayName("8.19 — \"until end of turn\" ends with the current turn, whoever's turn it is")
    void untilEndOfTheCurrentTurn() {
        ScenarioResult result = run(scenario().turn(4).active(P2).shards(P2, 1).unit(P2, "neutral.shardling")
                        .unit(P1, "test.martyr").unit(P1, "neutral.shardling").deck(P1, "neutral.shardling").build(),
                attack("neutral.shardling").on(unit("test.martyr")),
                declineIntercept(),
                endTurn());

        // The Martyr's buff is P1's, but it was given during P2's turn: it ends with P2's turn.
        Unit shardling = Pick.nthUnit(result.state(), "neutral.shardling", 2);
        assertThat(shardling.defense()).isEqualTo(3);
        assertThat(shardling.modifiers()).isEmpty();
        assertThat(trace(result)).containsSubsequence("AbilityTriggered[9.3]", "Modified[8.5]",
                "ModifierExpired[5.4.2, 8.5]", "TurnEnded[5.4]", "TurnStarted[5.2]");
        assertThat(result.state().active()).isEqualTo(P1);
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
    @DisplayName("8.7 — a player discards the cards they choose, even during the other player's turn")
    void discardChosen() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.mind-rot")
                        .hand(P2, "ember.spark-dart", "neutral.shardling", "neutral.tempest").build(),
                play("test.mind-rot"), discard("ember.spark-dart", "neutral.tempest"));

        Decision choice = result.decisions().get(1);
        assertThat(choice.player()).isEqualTo(P2);
        assertThat(choice.kind()).isEqualTo(DecisionKind.CHOOSE_CARDS);
        assertThat(choice.actions()).hasSize(3);
        assertThat(result.player(P2).hand()).extracting(inHand -> inHand.card().card())
                .containsExactly(new CardId("neutral.shardling"));
        assertThat(result.player(P2).graveyard()).extracting(CardInstance::card)
                .containsExactly(new CardId("ember.spark-dart"), new CardId("neutral.tempest"));
        assertThat(trace(result)).containsSubsequence("CardDiscarded[8.7]", "CardDiscarded[8.7]",
                "SpellResolved[6.3, 3.5]");
    }

    @Test
    @DisplayName("8.7 — a hand with no more cards than asked is discarded whole, without a choice")
    void discardWholeHand() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.mind-rot")
                        .hand(P2, "ember.spark-dart").build(),
                play("test.mind-rot"));

        assertThat(result.pending().orElseThrow().kind()).isEqualTo(DecisionKind.MAIN);
        assertThat(result.player(P2).hand()).isEmpty();
        assertThat(result.player(P2).graveyard()).hasSize(1);
    }

    @Test
    @DisplayName("8.7 — a random discard is drawn with the game's generator")
    void discardAtRandom() {
        GameState start = scenario().shards(P1, 1).hand(P1, "test.purge")
                .hand(P2, "ember.spark-dart", "neutral.shardling", "neutral.tempest").build();

        ScenarioResult result = run(start, play("test.purge"));
        ScenarioResult again = run(start, play("test.purge"));

        assertThat(result.decisions()).hasSize(1);
        assertThat(result.player(P2).hand()).hasSize(2);
        assertThat(result.events(GameEvent.CardDiscarded.class)).hasSize(1)
                .isEqualTo(again.events(GameEvent.CardDiscarded.class));
    }

    @Test
    @DisplayName("8.8 — a returned unit goes back to its owner's hand without dying: only \"Departure\" triggers")
    void returnToHand() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.recede")
                        .unit(P2, "test.guard", unit -> unit.defense(1)).deck(P2, "neutral.shardling").build(),
                play("test.recede").on(unit("test.guard")));

        assertThat(result.player(P2).units()).isEmpty();
        assertThat(result.player(P2).hand()).extracting(HandCard::id)
                .containsExactly(InstanceId.of(2), InstanceId.of(3));
        assertThat(trace(result)).containsSubsequence("ReturnedToHand[8.8, 6.7]", "SpellResolved[6.3, 3.5]",
                "AbilityTriggered[9.4]", "CardDrawn[8.6]");
        assertThat(result.events(GameEvent.UnitDestroyed.class)).isEmpty();
        assertThat(result.player(P1).hp()).isEqualTo(50);
    }

    @Test
    @DisplayName("8.8 — a card returned to a full hand goes to the graveyard; it still does not die")
    void returnToFullHand() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.recede").unit(P2, "ember.cinderling")
                        .hand(P2, "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                                "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                                "neutral.shardling", "neutral.shardling").build(),
                play("test.recede").on(unit("ember.cinderling")));

        assertThat(result.player(P2).graveyard()).extracting(CardInstance::card)
                .containsExactly(new CardId("ember.cinderling"));
        assertThat(trace(result)).contains("SentToGraveyardHandFull[8.8, 3.3]");
        assertThat(result.events(GameEvent.AbilityTriggered.class)).isEmpty();
        assertThat(result.player(P1).hp()).isEqualTo(50);
    }

    @Test
    @DisplayName("8.8 — a relic can be returned to its owner's hand")
    void returnRelic() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.uproot")
                        .relic(P2, "root.heartwood-shrine").build(),
                play("test.uproot").on(relic("root.heartwood-shrine")));

        assertThat(result.player(P2).relics()).isEmpty();
        assertThat(result.player(P2).hand()).extracting(HandCard::id).containsExactly(InstanceId.of(2));
        assertThat(trace(result)).contains("ReturnedToHand[8.8, 6.7]");
    }

    @Test
    @DisplayName("8.10 — a frozen unit cannot intercept for the rest of the turn, nor attack during the next one")
    void freeze() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "test.frost").unit(P1, "ember.cinderling")
                        .unit(P2, "neutral.shardling").unit(P2, "neutral.shard-construct")
                        .deck(P2, "neutral.shardling").build(),
                play("test.frost").on(unit("neutral.shardling")),
                attack("ember.cinderling").on(unit("neutral.shard-construct")),
                endTurn());

        assertThat(result.decisions()).extracting(Decision::kind)
                .containsExactly(DecisionKind.MAIN, DecisionKind.MAIN, DecisionKind.MAIN);
        assertThat(result.pending().orElseThrow().player()).isEqualTo(P2);
        assertThat(result.pending().orElseThrow().actions()).noneMatch(Action.Attack.class::isInstance);
        assertThat(result.unit("neutral.shardling").frozenThroughTurn()).isEqualTo(4);
        assertThat(trace(result)).contains("Frozen[8.10]");
    }

    @Test
    @DisplayName("8.10 — a frozen unit thaws at the start of the turn after the next one")
    void thaw() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.frost").unit(P2, "neutral.shardling")
                        .deck(P1, "neutral.shardling").deck(P2, "neutral.shardling").build(),
                play("test.frost").on(unit("neutral.shardling")),
                endTurn(), endTurn());

        assertThat(result.state().turn()).isEqualTo(5);
        assertThat(result.unit("neutral.shardling").frozenThroughTurn()).isZero();
        assertThat(trace(result)).containsSubsequence("Frozen[8.10]", "TurnStarted[5.2]", "TurnStarted[5.2]",
                "UnitThawed[8.10]", "ShardsRefilled[4.1, 4.2]");
    }

    @Test
    @DisplayName("8.12 — Shards gained for this turn can go above the max, and are lost at the end of the turn")
    void gainShardsThisTurn() {
        ScenarioResult played = run(scenario().shards(P1, 1).maxShards(P1, 1).hand(P1, "test.surge").build(),
                play("test.surge"));
        ScenarioResult ended = run(played.state(), endTurn());

        assertThat(played.player(P1).shards()).isEqualTo(new Shards(1, 2, 0));
        assertThat(trace(played)).contains("ShardsGained[8.12]");
        assertThat(ended.player(P1).shards().available()).isZero();
    }

    @Test
    @DisplayName("8.12 — Gain Shards can give one more max Shard, up to 10")
    void gainMaxShard() {
        ScenarioResult below = run(scenario().shards(P1, 3).maxShards(P1, 5).hand(P1, "root.deepening-roots").build(),
                play("root.deepening-roots"));
        ScenarioResult atCap = run(scenario().shards(P1, 10).hand(P1, "root.deepening-roots").build(),
                play("root.deepening-roots"));

        assertThat(below.player(P1).shards()).isEqualTo(new Shards(6, 1, 0));
        assertThat(atCap.player(P1).shards()).isEqualTo(new Shards(10, 8, 0));
        assertThat(atCap.events(GameEvent.ShardsGained.class)).extracting(GameEvent.ShardsGained::amount)
                .containsExactly(0);
    }

    @Test
    @DisplayName("8.13 — Recall returns the unit card its controller chooses from their graveyard to their hand")
    void recall() {
        GameState start = scenario().shards(P1, 4).hand(P1, "ember.rise-from-cinders")
                .graveyard(P1, "ember.cinderling", "ember.spark-dart", "neutral.shardling").build();

        ScenarioResult result = run(start, play("ember.rise-from-cinders").on(graveyardCard("neutral.shardling")));

        assertThat(result.decisions().getFirst().actions()).filteredOn(Action.PlayCard.class::isInstance)
                .extracting(action -> ((Action.PlayCard) action).targets())
                .containsExactly(List.of(new TargetRef.GraveyardCardTarget(InstanceId.of(2))),
                        List.of(new TargetRef.GraveyardCardTarget(InstanceId.of(4))));
        assertThat(result.player(P1).hand()).extracting(HandCard::id).containsExactly(InstanceId.of(4));
        assertThat(result.player(P1).graveyard()).extracting(CardInstance::card).containsExactly(
                new CardId("ember.cinderling"), new CardId("ember.spark-dart"), new CardId("ember.rise-from-cinders"));
        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3]", "Recalled[8.13]", "SpellResolved[6.3, 3.5]");
    }

    @Test
    @DisplayName("8.20 — Recall into a full hand does nothing: the card stays in the graveyard")
    void recallIntoFullHand() {
        GameState start = scenario().shards(P1, 1)
                .hand(P1, "test.second-wind", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                        "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                        "neutral.shardling", "neutral.shardling")
                .deck(P1, "ember.spark-dart").graveyard(P1, "ember.cinderling").build();

        ScenarioResult result = run(start, play("test.second-wind").on(graveyardCard("ember.cinderling")));

        assertThat(result.player(P1).hand()).hasSize(10);
        assertThat(result.player(P1).graveyard()).extracting(CardInstance::card)
                .containsExactly(new CardId("ember.cinderling"), new CardId("test.second-wind"));
        assertThat(trace(result)).containsSubsequence("CardDrawn[8.6]", "RecallFailed[8.13, 8.20]");
    }

    @Test
    @DisplayName("8.14 — a stat aura gives its bonus to every unit of its group while its card is on the board")
    void statAura() {
        ScenarioResult result = run(scenario().shards(P1, 1).relic(P1, "tide.coral-font")
                        .unit(P1, "neutral.shardling").unit(P2, "root.sprout").unit(P2, "neutral.shard-construct")
                        .build(),
                attack("neutral.shardling").on(unit("neutral.shard-construct")),
                interceptWith("root.sprout"));

        Unit mine = result.unit("neutral.shardling");
        assertThat(mine.defense()).isEqualTo(5);
        assertThat(mine.maxDefense()).isEqualTo(5);
        assertThat(result.events(GameEvent.UnitDamaged.class)).extracting(GameEvent.UnitDamaged::amount)
                .containsExactly(4);
        assertThat(result.unit("neutral.shard-construct").maxDefense()).isEqualTo(9);
        assertThat(trace(result)).containsSubsequence("AuraApplied[8.14]", "UnitDamaged[8.1]", "UnitDestroyed[6.6]");
    }

    @Test
    @DisplayName("8.14 — when a stat aura stops applying, its defense bonus ends like an expiring bonus")
    void statAuraEnds() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.shatter").relic(P2, "tide.coral-font")
                        .unit(P2, "neutral.shardling").unit(P2, "neutral.shardling", unit -> unit.defense(2)).build(),
                play("test.shatter").on(relic("tide.coral-font")));

        // 3/3 and 2/3 under +1/+2: 5/5 and 4/5, then back to a max of 3: 3/3 and 3/3.
        assertThat(result.player(P2).units()).extracting(Unit::defense, Unit::maxDefense, Unit::attackBonus)
                .containsExactly(tuple(3, 3, 0), tuple(3, 3, 0));
        assertThat(trace(result)).containsSubsequence("RelicDestroyed[8.2]", "AuraRemoved[8.14]", "AuraRemoved[8.14]");
    }

    @Test
    @DisplayName("8.14 — a stat aura's defense malus can destroy a unit, even one that just arrived")
    void statAuraMalusKills() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.cinderling").relic(P2, "test.blight")
                        .build(),
                play("ember.cinderling"));

        assertThat(result.player(P1).units()).isEmpty();
        assertThat(trace(result)).containsSubsequence("UnitArrived[6.3, 6.5]", "AuraApplied[8.14]",
                "UnitDestroyed[6.6]", "AbilityTriggered[9.3]", "PlayerDamaged[8.1]");
    }

    @Test
    @DisplayName("8.21 — when a stat aura's defense malus stops applying, the unit gets back what it lost")
    void statAuraMalusEnds() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.shatter")
                        .unit(P1, "neutral.shard-construct", unit -> unit.defense(5)).relic(P2, "test.blight").build(),
                play("test.shatter").on(relic("test.blight")));

        // 5/9 under -1/-2: 3/7; the malus ends and gives its 2 back: 5/9.
        Unit construct = result.unit("neutral.shard-construct");
        assertThat(construct.defense()).isEqualTo(5);
        assertThat(construct.maxDefense()).isEqualTo(9);
        assertThat(trace(result)).containsSubsequence("AuraApplied[8.14]", "AuraRemoved[8.14, 8.21]");
    }

    @Test
    @DisplayName("8.14 — a cost aura changes what cards of its type cost for the player it names")
    void costAura() {
        GameState start = scenario().shards(P1, 1).hand(P1, "ember.spark-dart", "neutral.shardling")
                .relic(P1, "test.forge").relic(P2, "test.tithe").unit(P2, "root.sprout").build();

        // Spark Dart: 1 + 1 from the opponent's Tithe Stone. Shardling: 1 - 1 + 1.
        assertThat(actions(start)).filteredOn(Action.PlayCard.class::isInstance)
                .extracting(action -> ((Action.PlayCard) action).card()).containsExactly(InstanceId.of(2));
        ScenarioResult result = run(start, play("neutral.shardling"));
        assertThat(result.events(GameEvent.CardPlayed.class).getFirst().cost()).isEqualTo(1);
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
