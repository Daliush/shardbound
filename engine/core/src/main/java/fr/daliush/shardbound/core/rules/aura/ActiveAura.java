package fr.daliush.shardbound.core.rules.aura;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** An aura printed on a card that is on the board, with that card's controller (9.7). */
record ActiveAura(CardInstance source, PlayerId controller, Effect aura) {

    private record OnBoard(CardInstance card, PlayerId controller, int arrivalSeq) {}

    /** Every aura on the given boards, from the card that arrived first. */
    static List<ActiveAura> on(CardCatalog catalog, PlayerState first, PlayerState second) {
        return Stream.of(first, second)
                .flatMap(player -> Stream.concat(
                        player.units().stream().map(unit -> new OnBoard(unit.asCard(), unit.controller(),
                                unit.arrivalSeq())),
                        player.relics().stream().map(relic -> new OnBoard(relic.asCard(), relic.controller(),
                                relic.arrivalSeq()))))
                .sorted(Comparator.comparingInt(OnBoard::arrivalSeq))
                .flatMap(card -> Triggers.abilitiesOf(catalog.card(card.card().card())).stream()
                        .filter(ability -> ability.trigger() == Trigger.CONTINUOUS)
                        .flatMap(ability -> ability.effects().stream())
                        .map(aura -> new ActiveAura(card.card(), card.controller(), aura)))
                .toList();
    }
}
