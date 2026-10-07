package fr.daliush.shardbound.core.rules.play;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectList;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.board.Arrivals;
import fr.daliush.shardbound.core.rules.board.Departures;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Optional;

/**
 * 6.3: paying a card's cost and its sacrifice cost, then the unit or relic arrives, or the spell applies its effects.
 */
public final class CardPlay {

    private CardPlay() {
    }

    public static void resolve(Game game, Step.ResolvePlay step) {
        PlayerId player = step.player();
        Action.PlayCard play = step.play();
        HandCard inHand = game.player(player).handCard(play.card()).orElseThrow();
        CardDefinition card = game.catalog().card(inHand.card().card());
        // Targets were chosen on the board as it was when the card was played (10.5).
        List<List<TargetRef>> chosen = card instanceof SpellCard spell
                ? ChoiceSlots.perEffect(game, player, spell.effects(), play.targets())
                : List.of();

        int cost = Costs.toPlay(game.catalog(), game.player(player), game.player(player.opponent()), inHand,
                play.overcharge());
        game.updatePlayer(player, state -> state.withShards(state.shards().pay(cost)).removeFromHand(play.card()));
        game.emit(new GameEvent.CardPlayed(player, inHand.card(), cost, play.overcharge()));
        // 6.3, 8.3: the units paid die together; their abilities wait until the card has resolved (9.11).
        for (InstanceId sacrificed : play.sacrificed()) {
            Departures.sacrifice(game, game.unit(sacrificed).orElseThrow(), List.of("6.3", "8.3"));
        }
        if (play.overcharge()) {
            lockShards(game, player);
        }
        switch (card) {
            case UnitCard ignored -> Arrivals.unit(game, inHand.card(), player);
            case RelicCard ignored -> Arrivals.relic(game, inHand.card(), player);
            case SpellCard spell -> cast(game, player, inHand.card(), spell, chosen);
        }
    }

    /** 11.4.2, 11.4.3: each overcharged card locks 2 of its player's Shards on their next turn. */
    private static void lockShards(Game game, PlayerId player) {
        game.updatePlayer(player, state -> state.withShards(state.shards().withLocked(Costs.OVERCHARGE_LOCK)));
        game.emit(new GameEvent.ShardsLocked(player, Costs.OVERCHARGE_LOCK,
                game.player(player).shards().lockedNextTurn()));
    }

    /** 9.1: the spell's effects, then it goes to the graveyard. */
    private static void cast(Game game, PlayerId player, CardInstance card, SpellCard spell,
                             List<List<TargetRef>> chosen) {
        EffectSource source = new EffectSource(new EffectList.SpellEffects(spell.id()), card.id(), player,
                Optional.empty());
        game.push(new Step.ResolveEffects(source, 0, chosen), new Step.FinishSpell(card));
    }

    public static void finishSpell(Game game, CardInstance spell) {
        game.updatePlayer(spell.owner(), state -> state.addToGraveyard(spell));
        game.emit(new GameEvent.SpellResolved(spell));
    }
}
