package fr.daliush.shardbound.core.text;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Duration;
import fr.daliush.shardbound.core.content.GainMode;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.testing.TestCards;
import fr.daliush.shardbound.core.testing.TestContent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DescribersTest {

    private final EventDescriber events = new EventDescriber(TestCards.CATALOG);
    private final ActionDescriber actions = new ActionDescriber(TestCards.CATALOG);
    private final DecisionDescriber decisions = new DecisionDescriber(TestCards.CATALOG);

    @Test
    void describesEventsFromEachPlayersPointOfView() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "ember.spark-dart")
                        .unit(P1, "ember.cinderling").unit(P2, "root.sprout").unit(P2, "neutral.shard-construct").build(),
                play("ember.spark-dart").on(unit("root.sprout")),
                attack("ember.cinderling").on(unit("neutral.shard-construct")));

        assertThat(describe(result, P1)).contains(
                "You play Spark Dart (1 Shard).",
                "Sprout #3 takes 3 damage.",
                "Sprout #3 is destroyed.",
                "Sprout #3 vanishes.",
                "Spark Dart goes to your graveyard.",
                "Cinderling #2 attacks Shard Construct #4 with Flick.");
        assertThat(describe(result, P2)).contains(
                "Your opponent plays Spark Dart (1 Shard).",
                "Spark Dart goes to your opponent's graveyard.");
    }

    @Test
    void hidesTheCardAnOpponentDraws() {
        GameEvent.CardDrawn drawn = new GameEvent.CardDrawn(P2, Optional.empty(), List.of("5.2.3"));

        assertThat(events.describe(drawn, P1)).isEqualTo("Your opponent draws a card.");
    }

    @Test
    void labelsTheActionsOfADecision() {
        GameState start = scenario().shards(P1, 1).hand(P1, "ember.spark-dart").unit(P1, "ember.cinderling")
                .unit(P2, "root.sprout").build();
        GameState state = ENGINE.resume(start).state();
        Decision decision = state.pending().orElseThrow();

        assertThat(decision.actions()).extracting(action -> actions.describe(action, state, P1)).containsExactly(
                "Play Spark Dart (1 Shard) on Sprout #3",
                "Cinderling #2 attacks Sprout #3 with Flick (1 Shard)",
                "End your turn");
    }

    @Test
    void asksEachDecisionFromTheDecidingPlayersPointOfView() {
        GameState setup = ENGINE.newGame(new GameSetup(TestContent.content().deck("ember-starter"),
                TestContent.content().deck("root-starter"), 1)).state();
        GameState main = ENGINE.resume(scenario().build()).state();
        ScenarioResult attacked = run(scenario().shards(P1, 1).unit(P1, "ember.cinderling")
                        .unit(P2, "neutral.shard-construct").unit(P2, "neutral.shardling").build(),
                attack("ember.cinderling").on(unit("neutral.shard-construct")));
        ScenarioResult arrived = run(scenario().shards(P1, 1).hand(P1, "test.watcher")
                        .unit(P2, "neutral.shard-construct").unit(P2, "root.thornback-ancient").build(),
                play("test.watcher"));

        assertThat(prompt(setup)).isEqualTo("Keep your opening hand, or shuffle it back and draw 4?");
        assertThat(prompt(main)).isEqualTo("Your turn: play a card, attack, or end your turn.");
        assertThat(prompt(attacked.state())).isEqualTo(
                "Cinderling #1 attacks your Shard Construct #2 with Flick (3 damage). Intercept with another unit?");
        assertThat(prompt(arrived.state())).isEqualTo("Choose a target for the Arrival ability of Watcher #1.");
    }

    @Test
    void describesSacrifices() {
        GameState start = scenario().shards(P1, 2).hand(P1, "ember.pyre-offering", "test.ritual")
                .unit(P1, "ember.cinderling").unit(P1, "neutral.shardling").unit(P2, "neutral.shard-construct")
                .build();
        GameState main = ENGINE.resume(start).state();
        ScenarioResult ritual = run(start, play("test.ritual"));

        assertThat(labels(main))
                .contains("Play Pyre Offering (1 Shard) on Shard Construct #5, sacrificing Cinderling #3");
        assertThat(prompt(ritual.state())).isEqualTo("Choose 1 unit to sacrifice for Blood Ritual #2.");
        assertThat(labels(ritual.state())).containsExactly("Sacrifice Cinderling #3", "Sacrifice Shardling #4");
        assertThat(events.describe(new GameEvent.SacrificeFailed(P2, 2, 1), P1))
                .isEqualTo("Your opponent cannot sacrifice 2 units (only 1 on their board): nothing more happens.");
    }

    @Test
    void describesDiscards() {
        ScenarioResult mindRot = run(scenario().shards(P1, 1).hand(P1, "test.mind-rot")
                        .hand(P2, "ember.spark-dart", "neutral.shardling", "neutral.tempest").build(),
                play("test.mind-rot"));
        CardInstance dart = new CardInstance(InstanceId.of(2), new CardId("ember.spark-dart"), P2);

        assertThat(prompt(mindRot.state())).isEqualTo("Choose 2 cards to discard for your opponent's Mind Rot #1.");
        assertThat(labels(mindRot.state())).containsExactly("Discard Spark Dart #2, Shardling #3",
                "Discard Spark Dart #2, Tempest #4", "Discard Shardling #3, Tempest #4");
        assertThat(events.describe(new GameEvent.CardDiscarded(P2, dart, GameEvent.DiscardReason.EFFECT,
                List.of("8.7")), P1)).isEqualTo("Your opponent discards Spark Dart.");
    }

    @Test
    void describesReturnsToHand() {
        CardInstance cinderling = new CardInstance(InstanceId.of(1), new CardId("ember.cinderling"), P2);

        assertThat(events.describe(new GameEvent.ReturnedToHand(cinderling), P1))
                .isEqualTo("Cinderling #1 returns to your opponent's hand.");
        assertThat(events.describe(new GameEvent.SentToGraveyardHandFull(cinderling), P2))
                .isEqualTo("Your hand is full: Cinderling #1 goes to the graveyard.");
    }

    @Test
    void describesFreezes() {
        CardInstance shardling = new CardInstance(InstanceId.of(3), new CardId("neutral.shardling"), P2);

        assertThat(events.describe(new GameEvent.Frozen(shardling, 4), P1))
                .isEqualTo("Shardling #3 is frozen until the end of turn 4.");
        assertThat(events.describe(new GameEvent.UnitThawed(shardling), P1)).isEqualTo("Shardling #3 thaws.");
    }

    @Test
    void describesShardsGained() {
        assertThat(events.describe(new GameEvent.ShardsGained(P1, 2, GainMode.THIS_TURN), P1))
                .isEqualTo("You gain 2 Shards this turn.");
        assertThat(events.describe(new GameEvent.ShardsGained(P2, 1, GainMode.MAX), P1))
                .isEqualTo("Your opponent gains 1 max Shard.");
        assertThat(events.describe(new GameEvent.ShardsGained(P1, 0, GainMode.MAX), P1))
                .isEqualTo("Your max Shards are already at 10.");
    }

    @Test
    void describesRecalls() {
        GameState start = scenario().shards(P1, 4).hand(P1, "ember.rise-from-cinders")
                .graveyard(P1, "ember.cinderling").build();
        CardInstance cinderling = new CardInstance(InstanceId.of(2), new CardId("ember.cinderling"), P1);

        assertThat(labels(ENGINE.resume(start).state())).containsExactly(
                "Play Rise from Cinders (4 Shards) on Cinderling #2 in your graveyard", "End your turn");
        assertThat(events.describe(new GameEvent.Recalled(cinderling), P1))
                .isEqualTo("Cinderling #2 returns from your graveyard to your hand.");
        assertThat(events.describe(new GameEvent.RecallFailed(cinderling), P2))
                .isEqualTo("Your opponent's hand is full: Cinderling #2 stays in the graveyard.");
    }

    @Test
    void describesAuras() {
        GameState start = scenario().shards(P1, 2).hand(P1, "ember.spark-dart").relic(P2, "test.tithe")
                .unit(P2, "root.sprout").build();
        CardInstance sprout = new CardInstance(InstanceId.of(3), new CardId("root.sprout"), P2);
        CardInstance font = new CardInstance(InstanceId.of(4), new CardId("tide.coral-font"), P2);

        assertThat(labels(ENGINE.resume(start).state())).contains("Play Spark Dart (2 Shards) on Sprout #3");
        assertThat(events.describe(new GameEvent.AuraApplied(sprout, font, 1, 2), P1))
                .isEqualTo("Coral Font #4 gives Sprout #3 +1/+2.");
        assertThat(events.describe(new GameEvent.AuraRemoved(sprout, font, 1, 2), P1))
                .isEqualTo("Sprout #3 loses the +1/+2 of Coral Font #4.");
    }

    @Test
    void describesModifiers() {
        CardInstance cinderling = new CardInstance(InstanceId.of(1), new CardId("ember.cinderling"), P2);

        assertThat(events.describe(new GameEvent.Modified(cinderling, 2, 0, Duration.END_OF_TURN), P1))
                .isEqualTo("Cinderling #1 gets +2/+0 until end of turn.");
        assertThat(events.describe(new GameEvent.Modified(cinderling, -2, 0, Duration.PERMANENT), P1))
                .isEqualTo("Cinderling #1 gets -2/+0.");
        assertThat(events.describe(new GameEvent.ModifierExpired(cinderling, -1, -2), P1))
                .isEqualTo("The -1/-2 on Cinderling #1 ends.");
    }

    @Test
    void describesAnchor() {
        CardInstance sentinel = new CardInstance(InstanceId.of(5), new CardId("root.root-sentinel"), P2);

        assertThat(events.describe(new GameEvent.AnchorPrevented(sentinel, GameEvent.Removal.DESTROY), P1))
                .isEqualTo("Root Sentinel #5 is anchored: it is not destroyed.");
        assertThat(events.describe(new GameEvent.AnchorPrevented(sentinel, GameEvent.Removal.SACRIFICE), P2))
                .isEqualTo("Root Sentinel #5 is anchored: it stays on the board, and the sacrifice counts as paid.");
        assertThat(events.describe(new GameEvent.UnitDoomed(sentinel), P1))
                .isEqualTo("Root Sentinel #5 is at 0 defense but anchored: it is doomed.");
        assertThat(events.describe(new GameEvent.DoomLifted(sentinel), P1))
                .isEqualTo("Root Sentinel #5 is no longer doomed.");
        assertThat(events.describe(new GameEvent.AnchorProtectionEnded(sentinel), P1))
                .isEqualTo("Root Sentinel #5 is no longer anchored.");
    }

    @Test
    void describesOvercharge() {
        GameState start = scenario().shards(P1, 4).hand(P1, "ember.ember-lance").build();

        assertThat(labels(ENGINE.resume(start).state())).containsExactly("Play Ember Lance (4 Shards)",
                "Play Ember Lance overcharged (2 Shards, locks 2 next turn)", "End your turn");
        assertThat(events.describe(new GameEvent.ShardsLocked(P1, 2, 4), P1))
                .isEqualTo("Your next turn will have 2 Shards more locked (4 in all).");
    }

    @Test
    void describesEchoes() {
        GameState start = scenario().shards(P1, 5).hand(P1, "neutral.crystal-rupture").unit(P1, "neutral.shardling")
                .unit(P1, "neutral.shard-construct").unit(P2, "test.twin-wyrm").build();
        ScenarioResult died = run(start, play("neutral.crystal-rupture").on(unit("test.twin-wyrm")));
        ScenarioResult ordered = run(start, play("neutral.crystal-rupture").on(unit("test.twin-wyrm")),
                Choices.echoOrder(1, 0));

        assertThat(prompt(died.state())).isEqualTo("Choose the order in which the echoes of Twin Wyrm #4 replay.");
        assertThat(labels(died.state())).containsExactly("Replay Fang first, then Frost Breath",
                "Replay Frost Breath first, then Fang");
        assertThat(describe(ordered, P1)).contains("Twin Wyrm #4 died: Frost Breath echoes at 50%.");
        assertThat(prompt(ordered.state())).isEqualTo("Choose a target for the echo of Frost Breath of Twin Wyrm #4.");
    }

    @Test
    void describesLinks() {
        GameState start = scenario().shards(P1, 1).hand(P1, "neutral.binding-thread").unit(P1, "neutral.shardling")
                .unit(P2, "root.sprout").build();
        CardInstance shardling = new CardInstance(InstanceId.of(2), new CardId("neutral.shardling"), P1);
        CardInstance sprout = new CardInstance(InstanceId.of(3), new CardId("root.sprout"), P2);

        assertThat(labels(ENGINE.resume(start).state()))
                .contains("Play Binding Thread (1 Shard) on Shardling #2, Sprout #3");
        assertThat(events.describe(new GameEvent.Linked(shardling, sprout), P1))
                .isEqualTo("Shardling #2 and Sprout #3 are linked.");
        assertThat(events.describe(new GameEvent.DamageShared(sprout, shardling, 1), P1))
                .isEqualTo("Shardling #2 takes 1 damage through its link with Sprout #3.");
        assertThat(events.describe(new GameEvent.LinkBroken(sprout, shardling), P1))
                .isEqualTo("Sprout #3 left the board: its link with Shardling #2 breaks.");
    }

    private List<String> labels(GameState state) {
        Decision decision = state.pending().orElseThrow();
        return decision.actions().stream().map(action -> actions.describe(action, state, decision.player())).toList();
    }

    private String prompt(GameState state) {
        return decisions.describe(state.pending().orElseThrow(), state);
    }

    private List<String> describe(ScenarioResult result, PlayerId viewer) {
        return ENGINE.eventsFor(result.events(), viewer).stream().map(event -> events.describe(event, viewer)).toList();
    }
}
