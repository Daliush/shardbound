package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import java.util.stream.Stream;

/** The cards of a scenario's starting state, and the ones its asker cannot see. */
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

    /** 3.2, 3.3, 3.7: the names of the cards hidden from the asker, in the opponent's hand and deck. */
    static List<String> hiddenNames(GameState state, PlayerId asker, CardCatalog catalog) {
        PlayerState opponent = state.player(asker.opponent());
        return Stream.concat(opponent.hand().stream().map(HandCard::card), opponent.deck().stream())
                .map(card -> catalog.card(card.card()).name())
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
