package fr.daliush.shardbound.core.text;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.rules.play.Costs;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A button label for an action, from the deciding player's point of view: "Play Spark Dart (1 Shard) on Sprout #61". */
public final class ActionDescriber {

    private final CardCatalog catalog;

    public ActionDescriber(CardCatalog catalog) {
        this.catalog = catalog;
    }

    public String describe(Action action, GameState state, PlayerId decider) {
        Wording w = new Wording(catalog, decider);
        return switch (action) {
            case Action.KeepHand ignored -> "Keep your hand";
            case Action.Mulligan ignored -> "Take a mulligan (draw 4)";
            case Action.PlayCard play -> play(w, play, state, decider);
            case Action.Attack attack -> attack(w, attack, state);
            case Action.Intercept intercept -> "Intercept with " + w.card(unit(state, intercept.interceptor()).asCard());
            case Action.DeclineIntercept ignored -> "Don't intercept";
            case Action.ChooseTarget choose -> "Choose " + target(w, choose.target(), state);
            case Action.ChooseCards choose -> chooseCards(w, choose, state);
            case Action.ChooseOrder order -> "Resolve in the order " + order.order();
            case Action.EndTurn ignored -> "End your turn";
        };
    }

    private String play(Wording w, Action.PlayCard play, GameState state, PlayerId decider) {
        HandCard inHand = state.player(decider).handCard(play.card()).orElseThrow();
        CardDefinition card = catalog.card(inHand.card().card());
        int cost = Costs.toPlay(card, inHand, play.overcharge());
        String label = "Play " + card.name() + (play.overcharge() ? " overcharged" : "") + " ("
                + Wording.shards(cost) + (play.overcharge() ? ", locks 2 next turn" : "") + ")";
        if (!play.targets().isEmpty()) {
            label += " on " + targets(w, play.targets(), state);
        }
        return play.sacrificed().isEmpty() ? label : label + ", sacrificing " + cards(w, play.sacrificed(), state);
    }

    /** "Sacrifice Cinderling #1": the verb comes from the effect that asks. */
    private String chooseCards(Wording w, Action.ChooseCards choose, GameState state) {
        String verb = switch (PausedStep.effect(state, catalog)) {
            case Effect.Sacrifice ignored -> "Sacrifice ";
            case Effect.Discard ignored -> "Discard ";
            case Effect effect -> throw new IllegalStateException(effect + " never asks for cards");
        };
        return verb + cards(w, choose.cards(), state);
    }

    private static String cards(Wording w, List<InstanceId> cards, GameState state) {
        return cards.stream().map(id -> w.card(findCard(state, id))).collect(Collectors.joining(", "));
    }

    private String attack(Wording w, Action.Attack attack, GameState state) {
        Unit attacker = unit(state, attack.attacker());
        AttackAbility ability = catalog.unit(attacker.card()).attacks().get(attack.attackIndex());
        String name = ability.name().orElse("its attack") + " (" + Wording.shards(Costs.toAttack(ability)) + ")";
        return attack.target()
                .map(target -> w.card(attacker.asCard()) + " attacks " + target(w, target, state) + " with " + name)
                .orElse(w.card(attacker.asCard()) + " uses " + name);
    }

    private String targets(Wording w, List<TargetRef> targets, GameState state) {
        return targets.stream().map(target -> target(w, target, state)).collect(Collectors.joining(", "));
    }

    private String target(Wording w, TargetRef target, GameState state) {
        return switch (target) {
            case TargetRef.UnitTarget unit -> w.card(unit(state, unit.id()).asCard());
            case TargetRef.RelicTarget relic -> w.card(Stream.of(state.p1(), state.p2())
                    .flatMap(player -> player.relic(relic.id()).stream()).findFirst().orElseThrow().asCard());
            case TargetRef.PlayerTarget player -> w.reflexive(player.player());
            case TargetRef.GraveyardCardTarget card -> w.card(findCard(state, card.id())) + " in "
                    + w.possessive(findCard(state, card.id()).owner()) + " graveyard";
        };
    }

    private static Unit unit(GameState state, InstanceId id) {
        return state.unit(id).orElseThrow(() -> new IllegalStateException("No unit " + id));
    }

    /** A card on the board, in a graveyard or in a hand. */
    private static CardInstance findCard(GameState state, InstanceId id) {
        return Stream.of(state.p1(), state.p2())
                .flatMap(player -> Stream.of(player.units().stream().map(Unit::asCard), player.graveyard().stream(),
                        player.hand().stream().map(HandCard::card)).flatMap(cards -> cards))
                .filter(card -> card.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No card " + id));
    }
}
