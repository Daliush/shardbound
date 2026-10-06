package fr.daliush.shardbound.api.dto;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.text.ActionDescriber;
import fr.daliush.shardbound.core.text.DecisionDescriber;
import java.util.List;
import java.util.stream.IntStream;

/** A decision for the player who must answer it: the engine's prompt, labels and actions, in canonical order. */
final class DecisionViews {

    private final ActionDescriber labels;
    private final DecisionDescriber prompts;

    DecisionViews(CardCatalog catalog) {
        this.labels = new ActionDescriber(catalog);
        this.prompts = new DecisionDescriber(catalog);
    }

    DecisionView of(Decision decision, GameState state) {
        List<ActionView> actions = IntStream.range(0, decision.actions().size())
                .mapToObj(index -> action(index, decision.actions().get(index), state, decision.player()))
                .toList();
        return new DecisionView(decision.id(), Wire.name(decision.kind()), prompts.describe(decision, state), actions);
    }

    private ActionView action(int index, Action action, GameState state, PlayerId decider) {
        String label = labels.describe(action, state, decider);
        return switch (action) {
            case Action.KeepHand ignored -> ActionView.plain(index, "keep_hand", label);
            case Action.Mulligan ignored -> ActionView.plain(index, "mulligan", label);
            case Action.PlayCard play -> ActionView.play(index, label, play.card().value(), play.overcharge(),
                    play.targets().stream().map(target -> target(target, decider)).toList(), ids(play.sacrificed()));
            case Action.Attack attack -> ActionView.attack(index, label, attack.attacker().value(),
                    attack.attackIndex(), attack.target().map(target -> List.of(target(target, decider)))
                            .orElse(List.of()));
            case Action.Intercept intercept -> ActionView.intercept(index, label, intercept.interceptor().value());
            case Action.DeclineIntercept ignored -> ActionView.plain(index, "decline_intercept", label);
            case Action.ChooseTarget choose -> ActionView.chooseTarget(index, label, target(choose.target(), decider));
            case Action.ChooseCards choose -> ActionView.chooseCards(index, label, ids(choose.cards()));
            case Action.ChooseOrder choose -> ActionView.chooseOrder(index, label, choose.order());
            case Action.EndTurn ignored -> ActionView.plain(index, "end_turn", label);
        };
    }

    private static TargetView target(TargetRef target, PlayerId viewer) {
        return switch (target) {
            case TargetRef.UnitTarget unit -> TargetView.card("unit", unit.id().value());
            case TargetRef.RelicTarget relic -> TargetView.card("relic", relic.id().value());
            case TargetRef.GraveyardCardTarget card -> TargetView.card("graveyard_card", card.id().value());
            case TargetRef.PlayerTarget player -> TargetView.player(Wire.side(viewer, player.player()));
        };
    }

    private static List<Integer> ids(List<InstanceId> ids) {
        return ids.stream().map(InstanceId::value).toList();
    }
}
