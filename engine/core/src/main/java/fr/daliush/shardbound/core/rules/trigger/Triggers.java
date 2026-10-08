package fr.daliush.shardbound.core.rules.trigger;

import fr.daliush.shardbound.core.content.Ability;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.resolution.QueuedTrigger;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Raises the abilities a card has for a trigger (rulebook section 9). */
public final class Triggers {

    private record BoardCard(CardInstance card, int arrivalSeq) {}

    private Triggers() {
    }

    public static void raise(Game game, CardInstance source, PlayerId controller, int arrivalSeq, Trigger trigger) {
        List<Ability> abilities = abilitiesOf(game.catalog().card(source.card()));
        for (int index = 0; index < abilities.size(); index++) {
            if (abilities.get(index).trigger() == trigger) {
                game.raise(new QueuedTrigger.TriggeredAbility(source, controller, trigger, index, arrivalSeq));
            }
        }
    }

    /** Raises a trigger for every unit and relic a player controls, oldest arrival first (9.5, 9.6). */
    public static void raiseForBoard(Game game, PlayerId player, Trigger trigger) {
        PlayerState state = game.player(player);
        Stream.concat(
                        state.units().stream().map(unit -> new BoardCard(unit.asCard(), unit.arrivalSeq())),
                        state.relics().stream().map(relic -> new BoardCard(relic.asCard(), relic.arrivalSeq())))
                .sorted(Comparator.comparingInt(BoardCard::arrivalSeq))
                .forEach(card -> raise(game, card.card(), player, card.arrivalSeq(), trigger));
    }

    public static List<Ability> abilitiesOf(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> unit.abilities();
            case RelicCard relic -> relic.abilities();
            case SpellCard ignored -> List.of();
        };
    }

    /** The rule that makes each trigger fire, for the trace. */
    public static String ruleOf(Trigger trigger) {
        return switch (trigger) {
            case ARRIVAL -> "9.2";
            case DEATH -> "9.3";
            case DEPARTURE -> "9.4";
            case TURN_START -> "9.5";
            case TURN_END -> "9.6";
            case CONTINUOUS -> "9.7";
            case ATTACK -> "9.9";
        };
    }
}
