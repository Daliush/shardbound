package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
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
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.json.GameJson;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.testing.TestContent;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 3: deck, hand, board, graveyard and hidden information. */
class ZoneRulesTest {

    @Test
    @DisplayName("3.3 — a card drawn into a full hand is discarded")
    void drawIntoFullHand() {
        ScenarioResult result = run(scenario()
                        .hand(P2, "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                                "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                                "neutral.shardling", "neutral.shardling")
                        .deck(P2, "neutral.tempest").build(),
                endTurn());

        GameEvent.CardDiscarded discarded = result.events(GameEvent.CardDiscarded.class).getFirst();
        assertThat(discarded.card().card()).isEqualTo(new CardId("neutral.tempest"));
        assertThat(discarded.rules()).containsExactly("5.2.3", "3.3");
        assertThat(result.player(P2).hand()).hasSize(10);
        assertThat(result.player(P2).graveyard()).extracting(CardInstance::card)
                .containsExactly(new CardId("neutral.tempest"));
    }

    @Test
    @DisplayName("3.4 — no unit can be played onto a full board")
    void fullBoardRefusesUnits() {
        GameState start = scenario().shards(P1, 5).hand(P1, "neutral.shardling")
                .unit(P1, "root.sprout").unit(P1, "root.sprout").unit(P1, "root.sprout")
                .unit(P1, "root.sprout").unit(P1, "root.sprout").unit(P1, "root.sprout").build();

        assertThat(mainActions(start)).noneMatch(Action.PlayCard.class::isInstance);
    }

    @Test
    @DisplayName("3.8 — a unit with a sacrifice cost can be played onto a full board: the sacrifice frees a place")
    void sacrificeFreesAPlace() {
        GameState start = scenario().shards(P1, 3).hand(P1, "ember.flamebound-zealot", "neutral.shardling")
                .unit(P1, "root.sprout").unit(P1, "root.sprout").unit(P1, "root.sprout")
                .unit(P1, "root.sprout").unit(P1, "root.sprout").unit(P1, "root.sprout").build();

        ScenarioResult result = run(start, play("ember.flamebound-zealot").sacrificing(unit("root.sprout", 2)));

        assertThat(result.decisions().getFirst().actions()).filteredOn(Action.PlayCard.class::isInstance)
                .hasSize(6).allMatch(action -> ((Action.PlayCard) action).card().equals(InstanceId.of(1)));
        assertThat(trace(result)).containsSubsequence("UnitSacrificed[6.3, 8.3]", "TokenVanished[3.6]",
                "UnitArrived[6.3, 6.5]");
        assertThat(result.player(P1).units()).hasSize(6);
        assertThat(result.unit("ember.flamebound-zealot").arrivalSeq()).isEqualTo(7);
    }

    @Test
    @DisplayName("3.4 — no relic can be played with 3 relics on the board")
    void fullRelicZone() {
        GameState start = scenario().shards(P1, 10).hand(P1, "root.heartwood-shrine")
                .relic(P1, "root.heartwood-shrine").relic(P1, "root.heartwood-shrine")
                .relic(P1, "root.heartwood-shrine").build();

        assertThat(mainActions(start)).noneMatch(Action.PlayCard.class::isInstance);
    }

    @Test
    @DisplayName("3.4 — a summon onto a full board does nothing")
    void summonOntoFullBoard() {
        ScenarioResult result = run(scenario().shards(P1, 3).hand(P1, "root.verdant-burst")
                        .unit(P1, "root.sprout").unit(P1, "root.sprout").unit(P1, "root.sprout")
                        .unit(P1, "root.sprout").unit(P1, "root.sprout").build(),
                play("root.verdant-burst"));

        assertThat(result.events(GameEvent.TokenSummoned.class)).hasSize(1);
        assertThat(result.events(GameEvent.SummonFailed.class).getFirst().rules()).containsExactly("8.9", "3.4");
        assertThat(result.player(P1).units()).hasSize(6);
    }

    @Test
    @DisplayName("3.5 — the graveyard keeps cards in the order they arrive")
    void graveyardOrder() {
        ScenarioResult result = run(scenario().shards(P1, 6)
                        .hand(P1, "ember.spark-dart", "neutral.crystal-rupture")
                        .unit(P2, "neutral.shardling").unit(P2, "neutral.shard-construct").build(),
                play("ember.spark-dart").on(unit("neutral.shardling")),
                play("neutral.crystal-rupture").on(unit("neutral.shard-construct")));

        assertThat(result.player(P1).graveyard()).extracting(card -> card.card().value())
                .containsExactly("ember.spark-dart", "neutral.crystal-rupture");
        assertThat(result.player(P2).graveyard()).extracting(card -> card.card().value())
                .containsExactly("neutral.shardling", "neutral.shard-construct");
    }

    @Test
    @DisplayName("3.6 — a token that leaves the board vanishes")
    void tokenVanishes() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "ember.spark-dart")
                        .unit(P2, "root.sprout").build(),
                play("ember.spark-dart").on(unit("root.sprout")));

        assertThat(result.events(GameEvent.TokenVanished.class)).hasSize(1);
        assertThat(result.player(P2).graveyard()).isEmpty();
    }

    @Test
    @DisplayName("3.6 — a token returned to hand vanishes too")
    void returnedTokenVanishes() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.flood").unit(P2, "root.sprout").build(),
                play("test.flood"));

        assertThat(trace(result)).contains("TokenVanished[8.8, 3.6]");
        assertThat(result.player(P2).hand()).isEmpty();
        assertThat(result.player(P2).graveyard()).isEmpty();
    }

    @Test
    @DisplayName("3.7 — a player sees how many cards the opponent holds, never which ones")
    void hiddenHand() {
        ScenarioResult result = run(scenario().deck(P2, "neutral.tempest").hand(P2, "neutral.shardling").build(),
                endTurn());

        PlayerView p1View = ENGINE.view(result.state(), P1, result.events());
        assertThat(p1View.opponent().handCount()).isEqualTo(2);
        GameEvent.CardDrawn seenByP1 = (GameEvent.CardDrawn) p1View.history().stream()
                .filter(GameEvent.CardDrawn.class::isInstance).findFirst().orElseThrow();
        assertThat(seenByP1.card()).isEmpty();
        PlayerView p2View = ENGINE.view(result.state(), P2, result.events());
        assertThat(p2View.history()).contains(result.events(GameEvent.CardDrawn.class).getFirst());
    }

    @Test
    @DisplayName("3.2 — no view shows the order of a deck, not even its owner's: only how many cards it holds")
    void deckOrderHidden() {
        Content content = TestContent.content();
        Transition started = ENGINE.newGame(new GameSetup(content.deck("ember-starter"), content.deck("root-starter"),
                7));
        GameState state = started.state();
        GameJson json = new GameJson();

        for (PlayerId viewer : PlayerId.values()) {
            PlayerView view = ENGINE.view(state, viewer, started.events());
            String seen = json.writeValue(view);
            assertThat(view.self().deckCount()).isEqualTo(state.player(viewer).deck().size());
            assertThat(view.opponent().deckCount()).isEqualTo(state.player(viewer.opponent()).deck().size());
            for (PlayerId owner : PlayerId.values()) {
                assertThat(state.player(owner).deck()).as("%s's deck, seen by %s", owner, viewer)
                        .noneMatch(card -> seen.contains(json.writeValue(card)));
            }
        }
    }

    private static List<Action> mainActions(GameState start) {
        return ENGINE.resume(start).state().pending().orElseThrow().actions();
    }
}
