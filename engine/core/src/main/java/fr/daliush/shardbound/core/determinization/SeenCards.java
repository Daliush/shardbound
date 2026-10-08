package fr.daliush.shardbound.core.determinization;

import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every card of the decks that the view shows: the viewer's hand and the opponent's known cards ({@code inHands}),
 * and the boards, the graveyards and a spell the resolution holds ({@code elsewhere}). Tokens are never in a deck
 * (2.4), so they are left out.
 */
record SeenCards(List<CardInstance> inHands, List<CardInstance> elsewhere) {

    SeenCards {
        inHands = List.copyOf(inHands);
        elsewhere = List.copyOf(elsewhere);
    }

    static SeenCards of(PlayerView view, List<HandCard> opponentKnownHand) {
        List<CardInstance> inHands = Stream.concat(view.self().hand().stream(), opponentKnownHand.stream())
                .map(HandCard::card)
                .toList();
        List<CardInstance> elsewhere = new ArrayList<>();
        Stream.concat(view.self().units().stream(), view.opponent().units().stream())
                .filter(unit -> !unit.token())
                .map(Unit::asCard)
                .forEach(elsewhere::add);
        Stream.concat(view.self().relics().stream(), view.opponent().relics().stream())
                .map(Relic::asCard)
                .forEach(elsewhere::add);
        elsewhere.addAll(view.self().graveyard());
        elsewhere.addAll(view.opponent().graveyard());
        for (Step step : view.resolution().steps()) {
            if (step instanceof Step.FinishSpell held) {
                elsewhere.add(held.spell().card());
            }
        }
        return new SeenCards(inHands, elsewhere);
    }

    List<CardInstance> ownedBy(PlayerId owner) {
        return Stream.concat(inHands.stream(), elsewhere.stream()).filter(card -> card.owner() == owner).toList();
    }

    List<CardInstance> outsideHandsOwnedBy(PlayerId owner) {
        return elsewhere.stream().filter(card -> card.owner() == owner).toList();
    }

    Set<InstanceId> ids() {
        return Stream.concat(inHands.stream(), elsewhere.stream()).map(CardInstance::id).collect(Collectors.toSet());
    }
}
