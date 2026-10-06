package fr.daliush.shardbound.core.rules.play;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.rules.board.BoardSpace;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.ArrayList;
import java.util.List;

/** Every legal {@code PlayCard}, in hand order, then target combinations in canonical order (spec §6.2). */
public final class PlayOptions {

    private PlayOptions() {
    }

    public static List<Action> list(Game game, PlayerId player) {
        PlayerState state = game.player(player);
        List<Action> plays = new ArrayList<>();
        for (HandCard inHand : state.hand()) {
            CardDefinition card = game.catalog().card(inHand.card().card());
            if (!EngineSupport.supports(card) || !fitsOnBoard(game, state, card)) {
                continue;
            }
            if (!state.shards().canPay(Costs.toPlay(card, inHand, false))) {
                continue;
            }
            for (List<TargetRef> targets : ChoiceSlots.combinations(game, player, effectsChosenOnPlay(card))) {
                plays.add(new Action.PlayCard(inHand.id(), false, targets, List.of()));
            }
        }
        return plays;
    }

    /** Only a spell picks targets when played; units and relics pick them when their abilities resolve. */
    private static List<Effect> effectsChosenOnPlay(CardDefinition card) {
        return card instanceof SpellCard spell ? spell.effects() : List.of();
    }

    private static boolean fitsOnBoard(Game game, PlayerState state, CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> BoardSpace.hasRoomFor(unit, state, game.catalog());
            case RelicCard ignored -> BoardSpace.hasRoomForRelic(state);
            case SpellCard ignored -> true;
        };
    }
}
