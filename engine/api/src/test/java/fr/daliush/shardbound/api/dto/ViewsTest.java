package fr.daliush.shardbound.api.dto;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.api.protocol.ProtocolJson;
import fr.daliush.shardbound.api.protocol.ServerMessage;
import fr.daliush.shardbound.core.bot.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ViewsTest {

    private static final Content CONTENT = ContentLoader.load(ContentLoader.find(Path.of("")).orElseThrow());
    private static final GameEngine ENGINE = new GameEngine(CONTENT.catalog());
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final GameViews views = new GameViews(ENGINE);
    private final EventViews events = new EventViews(ENGINE);
    private final ProtocolJson protocol = new ProtocolJson();

    @Test
    void eachSeatSeesItsOwnHandItsOwnDecisionAndOnlyTheSizeOfTheOtherHand() {
        Played game = play(1, 30);
        PlayerId decider = ENGINE.decision(game.state()).orElseThrow().player();

        GameView mine = views.of("g", 30, game.state(), decider);
        GameView theirs = views.of("g", 30, game.state(), decider.opponent());

        assertThat(mine.decision()).isPresent();
        assertThat(mine.waitingFor()).isEmpty();
        assertThat(theirs.decision()).isEmpty();
        assertThat(theirs.waitingFor()).hasValueSatisfying(waiting -> assertThat(waiting.player()).isEqualTo("opponent"));
        assertThat(theirs.opponent().orElseThrow().handCount()).isEqualTo(mine.you().hand().size());
        assertThat(mine.you().hand()).allSatisfy(card -> assertThat(card.cost()).isNotNegative());
    }

    @Test
    void webSocketViewsWriteNullFieldsExplicitlyButActionsOnlyCarryTheirOwnFields() {
        Played game = play(2, 20);
        PlayerId decider = ENGINE.decision(game.state()).orElseThrow().player();
        String other = protocol.write(new ServerMessage.Update(views.of("g", 20, game.state(), decider.opponent()),
                List.of()));
        String mine = protocol.write(new ServerMessage.Update(views.of("g", 20, game.state(), decider), List.of()));

        JsonNode waiting = JSON.readTree(other);
        assertThat(waiting.get("type").asString()).isEqualTo("update");
        assertThat(waiting.at("/view").has("decision")).isTrue();
        assertThat(waiting.at("/view/decision").isNull()).isTrue();
        assertThat(waiting.at("/view/result").isNull()).isTrue();

        JsonNode endTurn = JSON.readTree(mine).at("/view/decision/actions").valueStream()
                .filter(action -> action.get("type").asString().equals("end_turn"))
                .findFirst().orElseThrow();
        assertThat(endTurn.propertyNames()).containsExactly("index", "type", "label");
        assertThat(endTurn.get("label").asString()).isEqualTo("End your turn");
    }

    @Test
    void theWaitingViewShowsOnlyTheCreatorsDeck() {
        String json = protocol.write(new ServerMessage.State(views.waiting("g", CONTENT.deck("ember-starter")),
                List.of()));

        JsonNode view = JSON.readTree(json).get("view");
        assertThat(view.get("status").asString()).isEqualTo("waiting_for_opponent");
        assertThat(view.get("opponent").isNull()).isTrue();
        assertThat(view.get("activePlayer").isNull()).isTrue();
        assertThat(view.at("/you/deckCount").asInt()).isEqualTo(30);
        assertThat(view.at("/you/faction").asString()).isEqualTo("ember");
    }

    @Test
    void everyEventOfRandomGamesIsWrittenOneToOneFromTheEngineRecord() {
        for (int seed = 1; seed <= 30; seed++) {
            Played game = play(seed, Integer.MAX_VALUE);
            for (PlayerId seat : PlayerId.values()) {
                List<Map<String, Object>> seen = events.of(game.events(), seat);
                assertThat(seen).hasSameSizeAs(game.events());
                assertThat(seen).allSatisfy(event -> assertThat(event).containsKeys("type", "rules", "text"));
                assertThat(JSON.readTree(protocol.write(new ServerMessage.State(views.of("g", 0, game.state(), seat),
                        seen)))).isNotNull();
            }
        }
    }

    @Test
    void eventsKeepTheEnginesNamesAndFieldsSeenFromTheSeat() {
        Played game = play(3, Integer.MAX_VALUE);
        List<Map<String, Object>> seen = events.of(game.events(), P1);

        Map<String, Object> started = seen.getFirst();
        assertThat(started).containsEntry("type", "game_started").containsEntry("rules", List.of("5.1.1"));
        assertThat(started.get("firstPlayer")).isIn("you", "opponent");
        assertThat(seen).filteredOn(event -> event.get("type").equals("card_drawn")
                        && event.get("player").equals("opponent"))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.get("card")).isNull());
        assertThat(seen).filteredOn(event -> event.get("type").equals("unit_damaged"))
                .allSatisfy(event -> assertThat(event).containsKeys("unit", "amount"));
        assertThat(seen.getLast()).containsEntry("type", "game_ended");
        assertThat(seen.getLast().get("result")).isInstanceOf(GameView.ResultView.class);
        assertThat(seen.stream().map(event -> event.get("type"))).doesNotContain("damage_dealt");
        assertThat(events.of(game.events(), P2).getLast().get("result"))
                .isNotEqualTo(seen.getLast().get("result")).as("each seat sees its own outcome");
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
