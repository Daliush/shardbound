package fr.daliush.shardbound.api.controller.mappers.ws;

import fr.daliush.shardbound.api.controller.ws.message.ServerMessage;
import fr.daliush.shardbound.api.domain.bo.command.Rejection;
import fr.daliush.shardbound.api.domain.bo.view.StateView;
import fr.daliush.shardbound.api.domain.bo.view.UpdateView;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** What the domain says a seat must receive, as protocol messages. */
@Component
public class ServerMessageMapper {

    public ServerMessage state(StateView state) {
        return new ServerMessage.State(state.view(), state.history());
    }

    public ServerMessage update(UpdateView update) {
        return new ServerMessage.Update(update.view(), update.events());
    }

    /** {@code requestId} is null when the client's message was unreadable. */
    public ServerMessage rejected(String requestId, Rejection rejection) {
        return new ServerMessage.Rejected(requestId, rejection.reason().name().toLowerCase(Locale.ROOT),
                rejection.message());
    }
}
