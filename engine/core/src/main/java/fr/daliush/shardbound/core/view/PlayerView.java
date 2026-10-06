package fr.daliush.shardbound.core.view;

import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Optional;

/**
 * What one player may see: the only thing that leaves the server (spec §6.3).
 * {@code decision} is present only when the viewer must decide; {@code history} is already redacted.
 */
public record PlayerView(PlayerId viewer, int turn, PlayerId active, boolean yourTurn, SelfState self,
                         OpponentState opponent, Optional<Decision> decision, Optional<WaitingFor> waitingFor,
                         Optional<GameResult> result, Deck ownDeck, List<GameEvent> history) {

    public PlayerView {
        history = List.copyOf(history);
    }
}
