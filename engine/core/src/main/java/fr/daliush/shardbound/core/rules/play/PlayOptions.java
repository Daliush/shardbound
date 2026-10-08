package fr.daliush.shardbound.core.rules.play;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
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
 * Every legal {@code PlayCard}: cards in hand order, then without Overcharge before with it, then target combinations,
 * then the units sacrificed to pay the card, each in canonical order (spec §6.2).
 */
public final class PlayOptions {

    private PlayOptions() {
    }

    public static List<Action> list(Game game, PlayerId player) {
        PlayerState state = game.player(player);
        List<Action> plays = new ArrayList<>();
        for (HandCard inHand : state.hand()) {
            CardDefinition card = game.catalog().card(inHand.card().card());
            if (playedThisTurn(game, inHand) || !canMakeItsSacrifices(game, state, inHand, card)) {
                continue;
            }
            for (boolean overcharge : overchargeChoices(game, player, inHand, card)) {
                plays.addAll(plays(game, state, inHand, card, overcharge));
            }
        }
        return plays;
    }

    /** 11.4.1: a card with Overcharge may also be played overcharged; each way is offered if affordable (4.3). */
    private static List<Boolean> overchargeChoices(Game game, PlayerId player, HandCard inHand, CardDefinition card) {
        List<Boolean> choices = new ArrayList<>();
        for (boolean overcharge : card.has(Keyword.OVERCHARGE) ? List.of(false, true) : List.of(false)) {
            if (canPay(game, player, inHand, overcharge)) {
                choices.add(overcharge);
            }
        }
        return choices;
    }

    private static List<Action> plays(Game game, PlayerState state, HandCard inHand, CardDefinition card,
                                      boolean overcharge) {
        List<Action> plays = new ArrayList<>();
        List<List<InstanceId>> sacrifices = Sacrifices.options(state, game.catalog(), card.sacrificeCost());
        for (List<TargetRef> targets : ChoiceSlots.combinations(game, state.id(), effectsChosenOnPlay(inHand, card))) {
            for (List<InstanceId> sacrificed : sacrifices) {
                if (fitsOnBoard(game, state, card, sacrificed)) {
                    plays.add(new Action.PlayCard(inHand.id(), overcharge, targets, sacrificed));
                }
            }
        }
        return plays;
    }

    private static boolean canPay(Game game, PlayerId player, HandCard inHand, boolean overcharge) {
        PlayerState state = game.player(player);
        return state.shards().canPay(Costs.toPlay(game.catalog(), state, game.player(player.opponent()), inHand,
                overcharge));
    }

    /** 11.2.3: two steps of a Fracture card are never played in the same turn. */
    private static boolean playedThisTurn(Game game, HandCard inHand) {
        return inHand.lastFractureTurn() == game.turn();
    }

    /** 8.16: the sacrifice cost and a spell's Sacrifice effects, all of them, or the card cannot be played. */
    private static boolean canMakeItsSacrifices(Game game, PlayerState state, HandCard inHand, CardDefinition card) {
        int asked = Sacrifices.askedBy(effectsChosenOnPlay(inHand, card));
        return Sacrifices.canMake(state, game.catalog(), card.sacrificeCost() + asked);
    }

    /**
     * Only a spell applies effects when played, its own or those of its next Fracture step; units and relics pick
     * targets when their abilities resolve.
     */
    private static List<Effect> effectsChosenOnPlay(HandCard inHand, CardDefinition card) {
        return card instanceof SpellCard spell ? spell.effectsAtStep(inHand.fractureStep()) : List.of();
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
