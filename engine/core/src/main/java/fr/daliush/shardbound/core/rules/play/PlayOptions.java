package fr.daliush.shardbound.core.rules.play;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.rules.board.BoardSpace;
import fr.daliush.shardbound.core.rules.effect.Sacrifices;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.ArrayList;
import java.util.List;

/**
 * Every legal {@code PlayCard}: cards in hand order, then target combinations, then the units sacrificed to pay the
 * card, each in canonical order (spec §6.2).
 */
public final class PlayOptions {

    private PlayOptions() {
    }

    public static List<Action> list(Game game, PlayerId player) {
        PlayerState state = game.player(player);
        List<Action> plays = new ArrayList<>();
        for (HandCard inHand : state.hand()) {
            CardDefinition card = game.catalog().card(inHand.card().card());
            if (!EngineSupport.supports(card) || !canPay(game, player, inHand)
                    || !canMakeItsSacrifices(game, state, card)) {
                continue;
            }
            List<List<InstanceId>> sacrifices = Sacrifices.options(state, game.catalog(), card.sacrificeCost());
            for (List<TargetRef> targets : ChoiceSlots.combinations(game, player, effectsChosenOnPlay(card))) {
                for (List<InstanceId> sacrificed : sacrifices) {
                    if (fitsOnBoard(game, state, card, sacrificed)) {
                        plays.add(new Action.PlayCard(inHand.id(), false, targets, sacrificed));
                    }
                }
            }
        }
        return plays;
    }

    /** 4.3: Overcharge is offered separately (11.4). */
    private static boolean canPay(Game game, PlayerId player, HandCard inHand) {
        PlayerState state = game.player(player);
        return state.shards().canPay(Costs.toPlay(game.catalog(), state, game.player(player.opponent()), inHand,
                false));
    }

    /** 8.16: the sacrifice cost and a spell's Sacrifice effects, all of them, or the card cannot be played. */
    private static boolean canMakeItsSacrifices(Game game, PlayerState state, CardDefinition card) {
        int asked = card instanceof SpellCard spell ? Sacrifices.askedBy(spell.effects()) : 0;
        return Sacrifices.canMake(state, game.catalog(), card.sacrificeCost() + asked);
    }

    /** Only a spell picks targets when played; units and relics pick them when their abilities resolve. */
    private static List<Effect> effectsChosenOnPlay(CardDefinition card) {
        return card instanceof SpellCard spell ? spell.effects() : List.of();
    }

    private static boolean fitsOnBoard(Game game, PlayerState state, CardDefinition card,
                                       List<InstanceId> sacrificed) {
        return switch (card) {
            case UnitCard unit -> BoardSpace.hasRoomFor(unit, state, game.catalog(), sacrificed);
            case RelicCard ignored -> BoardSpace.hasRoomForRelic(state);
            case SpellCard ignored -> true;
        };
    }
}
