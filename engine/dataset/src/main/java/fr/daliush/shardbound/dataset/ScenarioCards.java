package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The cards of a scenario's starting state, and the ones its asker cannot know. */
final class ScenarioCards {

    private ScenarioCards() {
    }

    /** Every card in every zone of both players, by id, sorted. */
    static List<String> all(GameState state) {
        return Stream.of(state.p1(), state.p2())
                .flatMap(ScenarioCards::cards)
                .map(CardId::value)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 3.2, 3.3, 3.7: the names of the cards in the opponent's hand and deck, unless the asker knows them anyway: a
     * Fracture card already played (11.2.5), a card the opponent plays during the scenario, or the same card seen in a
     * public zone or in the asker's hand.
     */
    static List<String> hiddenNames(GameState start, ScenarioResult result, PlayerId asker, CardCatalog catalog) {
        PlayerState opponent = start.player(asker.opponent());
        Set<CardId> known = Stream.concat(
                        Stream.of(opponent.units().stream().map(Unit::card),
                                        opponent.relics().stream().map(Relic::card),
                                        opponent.graveyard().stream().map(CardInstance::card),
                                        cards(start.player(asker)),
                                        opponent.hand().stream().filter(inHand -> inHand.fractureStep() > 0)
                                                .map(inHand -> inHand.card().card()))
                                .flatMap(cards -> cards),
                        result.events(GameEvent.CardPlayed.class).stream()
                                .filter(played -> played.player() == asker.opponent())
                                .map(played -> played.card().card()))
                .collect(Collectors.toSet());
        return Stream.concat(opponent.hand().stream().map(HandCard::card), opponent.deck().stream())
                .map(CardInstance::card)
                .filter(card -> !known.contains(card))
                .map(card -> catalog.card(card).name())
                .distinct()
                .toList();
    }

    private static Stream<CardId> cards(PlayerState player) {
        return Stream.of(
                        player.deck().stream().map(CardInstance::card),
                        player.hand().stream().map(inHand -> inHand.card().card()),
                        player.units().stream().map(Unit::card),
                        player.relics().stream().map(Relic::card),
                        player.graveyard().stream().map(CardInstance::card))
                .flatMap(cards -> cards);
    }
}
