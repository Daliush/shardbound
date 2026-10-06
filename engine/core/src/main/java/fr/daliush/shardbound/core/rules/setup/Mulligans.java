package fr.daliush.shardbound.core.rules.setup;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.turn.CardDraws;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.List;

/** 5.1.3: each player, first player first, may once shuffle their hand back and draw one card fewer. */
public final class Mulligans {

    public static final int MULLIGAN_HAND_SIZE = 4;

    private Mulligans() {
    }

    public static void ask(Game game, PlayerId player) {
        game.ask(player, DecisionKind.MULLIGAN, List.of(new Action.KeepHand(), new Action.Mulligan()));
    }

    public static void answer(Game game, PlayerId player, Action action) {
        if (action instanceof Action.Mulligan) {
            takeMulligan(game, player);
        } else {
            game.emit(new GameEvent.HandKept(player));
        }
        game.updatePlayer(player, state -> state.withMulliganDecided());
        if (game.player(player.opponent()).mulliganDecided()) {
            game.push(new Step.StartTurn(game.firstPlayer()));
        } else {
            ask(game, player.opponent());
        }
    }

    private static void takeMulligan(Game game, PlayerId player) {
        game.updatePlayer(player, state -> {
            List<CardInstance> deck = new ArrayList<>(state.deck());
            state.hand().stream().map(HandCard::card).forEach(deck::add);
            game.rng().shuffle(deck);
            return state.withDeck(deck).withHand(List.of());
        });
        game.emit(new GameEvent.MulliganTaken(player));
        for (int i = 0; i < MULLIGAN_HAND_SIZE; i++) {
            CardDraws.draw(game, player, "5.1.3");
        }
    }
}
