package fr.daliush.shardbound.core.determinization;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.QueuedTrigger;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The fields of a game that a view does not carry, derived from what it does (spec §10). Arrival sequences only order
 * cards against each other, so the next one only has to come after every one that exists.
 */
record DerivedFields(PlayerId firstPlayer, int turn, boolean opponentMulliganDecided, int nextArrivalSeq,
                     int decisionSeq) {

    private static final String DECISION_ID_PREFIX = "d-";

    static DerivedFields of(PlayerView view) {
        return new DerivedFields(firstPlayer(view), view.turn(), opponentMulliganDecided(view), nextArrivalSeq(view),
                decisionSeq(view));
    }

    /** The first player has played ⌈turn / 2⌉ turns, the other player ⌊turn / 2⌋ (5.2.3 reads it). */
    int turnsTaken(PlayerId player) {
        return player == firstPlayer ? (turn + 1) / 2 : turn / 2;
    }

    /** 5.1.1: the history starts with it. */
    private static PlayerId firstPlayer(PlayerView view) {
        return view.history().stream()
                .filter(GameEvent.GameStarted.class::isInstance)
                .map(event -> ((GameEvent.GameStarted) event).firstPlayer())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The history must start with the game (5.1.1)"));
    }

    /** 5.1.3: the opponent kept their hand or took a mulligan. */
    private static boolean opponentMulliganDecided(PlayerView view) {
        PlayerId opponent = view.viewer().opponent();
        return view.history().stream().anyMatch(event -> event instanceof GameEvent.HandKept kept
                && kept.player() == opponent
                || event instanceof GameEvent.MulliganTaken mulligan && mulligan.player() == opponent);
    }

    /** After every card on the board and every queued ability, which keeps its card's place (9.8). */
    private static int nextArrivalSeq(PlayerView view) {
        IntStream units = Stream.concat(view.self().units().stream(), view.opponent().units().stream())
                .mapToInt(Unit::arrivalSeq);
        IntStream relics = Stream.concat(view.self().relics().stream(), view.opponent().relics().stream())
                .mapToInt(Relic::arrivalSeq);
        IntStream queued = view.resolution().queue().stream().mapToInt(QueuedTrigger::arrivalSeq);
        return IntStream.concat(IntStream.concat(units, relics), queued).max().orElse(0) + 1;
    }

    /** A decision's id is {@code "d-"} and its sequence number (spec §6). */
    private static int decisionSeq(PlayerView view) {
        String id = view.decision().orElseThrow().id();
        return Integer.parseInt(id.substring(DECISION_ID_PREFIX.length()));
    }
}
