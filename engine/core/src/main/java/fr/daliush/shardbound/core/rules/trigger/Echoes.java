package fr.daliush.shardbound.core.rules.trigger;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectList;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.resolution.QueuedTrigger;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 11.1 Echo: when a unit dies, each of its attack abilities with Echo is replayed at X%, on a new target its owner
 * chooses. An echo is not an attack (11.1.4): it costs nothing, cannot be intercepted and triggers no "Attack" ability.
 */
public final class Echoes {

    private Echoes() {
    }

    /** 9.3, 11.1.1: a unit that dies raises its echoes; one returned to hand does not die (11.1.5). */
    public static void raise(Game game, Unit unit) {
        UnitCard card = game.catalog().unit(unit.card());
        List<Integer> echoing = IntStream.range(0, card.attacks().size())
                .filter(index -> card.attacks().get(index).echo().isPresent())
                .boxed()
                .toList();
        if (!echoing.isEmpty()) {
            game.raise(new QueuedTrigger.Echoes(unit.asCard(), unit.controller(), echoing, unit.arrivalSeq()));
        }
    }

    /** One echo starts; with two, the owner first chooses their order (11.1.8). */
    public static void start(Game game, Step.StartEchoes step) {
        if (step.attacks().size() == 1) {
            game.push(begin(game, step, step.attacks().getFirst()));
            return;
        }
        List<Action> orders = List.of(new Action.ChooseOrder(step.attacks()),
                new Action.ChooseOrder(step.attacks().reversed()));
        game.pauseAndAsk(step, step.controller(), DecisionKind.CHOOSE_ORDER, orders);
    }

    /** The echoes start one after the other, in the order chosen. */
    public static void resume(Game game, Step.StartEchoes step, Action answer) {
        if (!(answer instanceof Action.ChooseOrder order)) {
            throw new IllegalStateException(answer + " does not answer a choice of order");
        }
        game.push(order.order().stream()
                .map(attack -> new Step.StartEchoes(step.unit(), step.controller(), List.of(attack)))
                .toArray(Step[]::new));
    }

    /** Emits the echo and returns its first step: its sacrifices and targets, the attack's new target first (10.6). */
    private static Step begin(Game game, Step.StartEchoes step, int attackIndex) {
        int percent = game.catalog().unit(step.unit().card()).attacks().get(attackIndex).echo().orElseThrow();
        game.emit(new GameEvent.EchoTriggered(step.unit(), attackIndex, percent));
        EffectList effects = new EffectList.EchoEffects(step.unit().card(), attackIndex);
        return new Step.ChooseTargets(new EffectSource(effects, step.unit().id(), step.controller(), Optional.empty()),
                List.of());
    }
}
