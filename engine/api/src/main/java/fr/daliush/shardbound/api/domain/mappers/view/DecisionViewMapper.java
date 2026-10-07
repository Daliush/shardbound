package fr.daliush.shardbound.api.domain.mappers.view;

import fr.daliush.shardbound.api.domain.bo.game.SeatSnapshot;
import fr.daliush.shardbound.api.domain.bo.view.ActionView;
import fr.daliush.shardbound.api.domain.bo.view.DecisionView;
import fr.daliush.shardbound.api.domain.bo.view.TargetView;
import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/** A decision for the player who must answer it: the engine's prompt, labels and actions, in canonical order. */
@Component
public class DecisionViewMapper {

    public DecisionView toView(Decision decision, SeatSnapshot.DecisionText text) {
        List<ActionView> actions = IntStream.range(0, decision.actions().size())
                .mapToObj(index -> action(index, decision.actions().get(index), text.labels().get(index),
                        decision.player()))
                .toList();
        return new DecisionView(decision.id(), Wire.name(decision.kind()), text.prompt(), actions);
    }

    private static ActionView action(int index, Action action, String label, PlayerId decider) {
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
