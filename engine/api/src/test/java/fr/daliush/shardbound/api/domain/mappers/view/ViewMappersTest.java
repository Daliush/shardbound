package fr.daliush.shardbound.api.domain.mappers.view;

import static fr.daliush.shardbound.api.testing.TestInstance.CONTENT;
import static fr.daliush.shardbound.api.testing.TestInstance.ENGINE;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.view.EventView;
import fr.daliush.shardbound.api.domain.bo.view.GameView;
import fr.daliush.shardbound.api.domain.bo.view.HandCardView;
import fr.daliush.shardbound.core.bot.random.RandomBot;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.json.CardParser;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ViewMappersTest {

    private final GameId game = GameId.random();
    private final SeatSnapshotMapper snapshots = new SeatSnapshotMapper(ENGINE);
    private final GameViewMapper views = new GameViewMapper(ENGINE, new DecisionViewMapper());
    private final EventViewMapper events = new EventViewMapper(ENGINE);

    @Test
    void eachSeatSeesItsOwnHandItsOwnDecisionAndOnlyTheSizeOfTheOtherHand() {
        Played played = play(1, 30);
        PlayerId decider = ENGINE.decision(played.state()).orElseThrow().player();

        GameView mine = views.toView(game, 30, snapshots.toSnapshot(played.state(), decider));
        GameView theirs = views.toView(game, 30, snapshots.toSnapshot(played.state(), decider.opponent()));

        assertThat(mine.decision()).hasValueSatisfying(decision -> assertThat(decision.actions())
                .allSatisfy(action -> assertThat(action.label()).isNotBlank()));
        assertThat(mine.waitingFor()).isEmpty();
        assertThat(theirs.decision()).isEmpty();
        assertThat(theirs.waitingFor()).hasValueSatisfying(waiting -> assertThat(waiting.player()).isEqualTo("opponent"));
        assertThat(theirs.opponent().orElseThrow().handCount()).isEqualTo(mine.you().hand().size());
        assertThat(mine.you().hand()).allSatisfy(card -> assertThat(card.cost()).isNotNegative());
    }

    @Test
    void aHandCardCostsWhatTheCostAurasOnTheBoardMakeItCost() {
        CardCatalog catalog = withCard("""
                { "id": "test.tithe", "name": "Tithe Stone", "faction": "neutral", "type": "relic", "cost": 1,
                  "abilities": [{ "trigger": "continuous", "effects": [
                    { "effect": "aura", "kind": "cost", "player": "opponent", "card_type": "any", "change": 1 }] }] }""");
        GameEngine engine = new GameEngine(catalog);
        GameState state = engine.resume(ScenarioBuilder.of(catalog).shards(P1, 2).hand(P1, "ember.spark-dart")
                .relic(P2, "test.tithe").build()).state();

        GameView view = new GameViewMapper(engine, new DecisionViewMapper())
                .toView(game, 1, new SeatSnapshotMapper(engine).toSnapshot(state, P1));

        assertThat(view.you().hand()).extracting(HandCardView::cost).containsExactly(2);
    }

    @Test
    void everyEventOfRandomGamesIsWrittenOneToOneFromTheEngineRecord() {
        for (int seed = 1; seed <= 30; seed++) {
            Played played = play(seed, Integer.MAX_VALUE);
            for (PlayerId seat : PlayerId.values()) {
                List<EventView> seen = events.toViews(played.events(), seat);
                assertThat(seen).hasSameSizeAs(played.events());
                assertThat(seen).allSatisfy(event -> assertThat(event.text()).isNotBlank());
            }
        }
    }

    @Test
    void eventsKeepTheEnginesNamesAndFieldsSeenFromTheSeat() {
        Played played = play(3, Integer.MAX_VALUE);
        List<EventView> seen = events.toViews(played.events(), P1);

        EventView started = seen.getFirst();
        assertThat(started.type()).isEqualTo("game_started");
        assertThat(started.rules()).containsExactly("5.1.1");
        assertThat(started.fields().get("firstPlayer")).isIn("you", "opponent");
        assertThat(seen).filteredOn(event -> event.type().equals("card_drawn")
                        && event.fields().get("player").equals("opponent"))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.fields().get("card")).isNull());
        assertThat(seen).filteredOn(event -> event.type().equals("unit_damaged"))
                .allSatisfy(event -> assertThat(event.fields()).containsKeys("unit", "amount"));
        assertThat(seen.getLast().type()).isEqualTo("game_ended");
        assertThat(seen.stream().map(EventView::type)).doesNotContain("damage_dealt");
        assertThat(events.toViews(played.events(), P2).getLast().fields().get("result"))
                .as("each seat sees its own outcome").isNotEqualTo(seen.getLast().fields().get("result"));
    }

    @Test
    void theWaitingViewShowsOnlyTheCreatorsDeck() {
        GameView view = views.waiting(game, CONTENT.deck("ember-starter"));

        assertThat(view.status()).isEqualTo(GameView.WAITING_FOR_OPPONENT);
        assertThat(view.opponent()).isEmpty();
        assertThat(view.activePlayer()).isEmpty();
        assertThat(view.you().deckCount()).isEqualTo(30);
        assertThat(view.you().faction()).isEqualTo("ember");
    }

    private static CardCatalog withCard(String json) {
        List<CardDefinition> cards = new ArrayList<>(CONTENT.catalog().all());
        cards.add(new CardParser().parse(JsonMapper.builder().build().readTree(json), "test card"));
        return new CardCatalog(cards);
    }

    private record Played(GameState state, List<GameEvent> events) {}

    private static Played play(long seed, int decisions) {
        Transition t = ENGINE.newGame(new GameSetup(CONTENT.deck("ember-starter"), CONTENT.deck("root-starter"), seed));
        List<GameEvent> log = new ArrayList<>(t.events());
        GameState state = t.state();
        RandomBot bot = new RandomBot(seed);
        for (int i = 0; i < decisions; i++) {
            Optional<Decision> decision = ENGINE.decision(state);
            if (decision.isEmpty()) {
                break;
            }
            t = ENGINE.apply(state, bot.choose(null, decision.get()));
            log.addAll(t.events());
            state = t.state();
        }
        return new Played(state, log);
    }
}
