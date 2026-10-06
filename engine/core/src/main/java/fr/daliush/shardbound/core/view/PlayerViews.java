package fr.daliush.shardbound.core.view;

import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.List;
import java.util.Optional;

/** Builds a player's view of a state. {@code history} must already be redacted for that player. */
public final class PlayerViews {

    private PlayerViews() {
    }

    public static PlayerView of(GameState state, PlayerId viewer, List<GameEvent> history) {
        PlayerState self = state.player(viewer);
        Optional<Decision> pending = state.isOver() ? Optional.empty() : state.pending();
        Optional<Decision> decision = pending.filter(d -> d.player() == viewer);
        Optional<WaitingFor> waitingFor = pending.filter(d -> d.player() != viewer)
                .map(d -> new WaitingFor(d.player(), d.kind()));
        return new PlayerView(viewer, state.turn(), state.active(), state.active() == viewer && state.turn() > 0,
                selfState(self), opponentState(state.player(viewer.opponent())), decision, waitingFor,
                state.result(), self.decklist(), history);
    }

    private static SelfState selfState(PlayerState player) {
        return new SelfState(player.faction(), player.hp(), PlayerState.MAX_HP, player.shards(), player.fatigue(),
                player.deck().size(), player.hand(), player.units(), player.relics(), player.graveyard(),
                player.mulliganDecided());
    }

    private static OpponentState opponentState(PlayerState player) {
        return new OpponentState(player.faction(), player.hp(), PlayerState.MAX_HP, player.shards(),
                player.fatigue(), player.deck().size(), player.hand().size(), player.units(), player.relics(),
                player.graveyard());
    }
}
