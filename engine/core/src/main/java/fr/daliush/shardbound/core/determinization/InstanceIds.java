package fr.daliush.shardbound.core.determinization;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.view.OpponentState;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The ids of the cards in the game. The engine numbers P1's deck from 1, then P2's, and tokens after both decks; an
 * unseen id says nothing about its card, and nothing is read into it (spec §10).
 */
final class InstanceIds {

    private final int p1Cards;
    private final int p2Cards;
    private final Set<InstanceId> seen;
    private final int highestToken;

    private InstanceIds(int p1Cards, int p2Cards, Set<InstanceId> seen, int highestToken) {
        this.p1Cards = p1Cards;
        this.p2Cards = p2Cards;
        this.seen = seen;
        this.highestToken = highestToken;
    }

    /** The opponent's cards are counted where they are: hand, deck, and seen elsewhere. */
    static InstanceIds of(PlayerView view, SeenCards seen) {
        PlayerId viewer = view.viewer();
        OpponentState opponent = view.opponent();
        int viewerCards = view.ownDeck().size();
        int opponentCards = opponent.handCount() + opponent.deckCount()
                + seen.outsideHandsOwnedBy(viewer.opponent()).size();
        int highestToken = view.history().stream()
                .filter(GameEvent.TokenSummoned.class::isInstance)
                .mapToInt(event -> ((GameEvent.TokenSummoned) event).unit().id().value())
                .max()
                .orElse(0);
        return viewer == PlayerId.P1
                ? new InstanceIds(viewerCards, opponentCards, seen.ids(), highestToken)
                : new InstanceIds(opponentCards, viewerCards, seen.ids(), highestToken);
    }

    /** The ids of {@code owner}'s cards that the view does not show, in ascending order. */
    List<InstanceId> unseenOf(PlayerId owner) {
        int first = owner == PlayerId.P1 ? 1 : p1Cards + 1;
        int last = owner == PlayerId.P1 ? p1Cards : p1Cards + p2Cards;
        return IntStream.rangeClosed(first, last).mapToObj(InstanceId::of).filter(id -> !seen.contains(id)).toList();
    }

    /** Beyond every id that exists: the id the next token gets (8.9). */
    int next() {
        return Math.max(p1Cards + p2Cards, highestToken) + 1;
    }
}
