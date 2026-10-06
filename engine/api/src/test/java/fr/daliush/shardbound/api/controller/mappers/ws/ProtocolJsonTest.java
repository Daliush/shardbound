package fr.daliush.shardbound.api.controller.mappers.ws;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.api.controller.ws.message.ClientMessage;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.view.StateView;
import fr.daliush.shardbound.api.testing.TestInstance;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProtocolJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TestInstance server = TestInstance.alone();
    private final ProtocolJson protocol = new ProtocolJson();
    private final ServerMessageMapper messages = new ServerMessageMapper();

    @Test
    void writesNullFieldsExplicitlyButActionsAndTargetsOnlyWithTheirOwnFields() {
        GameId id = server.creation.create(TestInstance.botGame(2)).game();
        StateView state = server.seatUpdates.state(id, P1);

        JsonNode message = JSON.readTree(protocol.write(messages.state(state)));

        assertThat(message.get("type").asString()).isEqualTo("state");
        assertThat(message.at("/view").has("waitingFor")).isTrue();
        assertThat(message.at("/view/result").isNull()).isTrue();
        JsonNode keepHand = message.at("/view/decision/actions/0");
        assertThat(keepHand.propertyNames()).containsExactly("index", "type", "label");
        assertThat(message.at("/view/you/units").isArray()).isTrue();
    }

    @Test
    void writesEachEventsFieldsBesideItsTypeRulesAndText() {
        GameId id = server.creation.create(TestInstance.botGame(2)).game();

        JsonNode first = JSON.readTree(protocol.write(messages.state(server.seatUpdates.state(id, P1))))
                .at("/history/0");

        assertThat(first.propertyNames()).containsExactly("type", "rules", "text", "firstPlayer");
        assertThat(first.get("type").asString()).isEqualTo("game_started");
    }

    @Test
    void readsTheClientsMessagesAndNothingElse() {
        assertThat(protocol.read("{ \"type\": \"act\", \"requestId\": \"c-1\", \"decisionId\": \"d-3\", \"action\": 2 }"))
                .hasValue(new ClientMessage.Act("c-1", "d-3", 2));
        assertThat(protocol.read("{ \"type\": \"sync\" }")).hasValue(new ClientMessage.Sync());
        assertThat(protocol.read("hello")).isEqualTo(Optional.empty());
        assertThat(protocol.read("{ \"type\": \"dance\" }")).isEmpty();
    }
}
