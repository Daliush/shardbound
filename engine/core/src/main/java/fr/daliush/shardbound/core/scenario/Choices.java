package fr.daliush.shardbound.core.scenario;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import java.util.Optional;

/** The usual choices of a scenario: {@code play("ember.spark-dart").on(unit("root.sprout"))}, {@code endTurn()}… */
public final class Choices {

    private Choices() {
    }

    public static Choice keepHand() {
        return exactly(new Action.KeepHand());
    }

    public static Choice mulligan() {
        return exactly(new Action.Mulligan());
    }

    public static Choice endTurn() {
        return exactly(new Action.EndTurn());
    }

    public static Choice declineIntercept() {
        return exactly(new Action.DeclineIntercept());
    }

    public static Choice interceptWith(String card) {
        return (state, decision) -> {
            Unit interceptor = Pick.nthUnit(state, card, 1);
            return Choice.single(decision, action -> action.equals(new Action.Intercept(interceptor.id())),
                    "intercept with " + card);
        };
    }

    public static Choice chooseTarget(Pick target) {
        return (state, decision) -> {
            TargetRef wanted = target.in(state);
            return Choice.single(decision, action -> action.equals(new Action.ChooseTarget(wanted)),
                    "choose " + wanted);
        };
    }

    public static PlayChoice play(String card) {
        return new PlayChoice(card, List.of());
    }

    public static AttackChoice attack(String attacker) {
        return new AttackChoice(attacker, 0, Optional.empty());
    }

    private static Choice exactly(Action wanted) {
        return (state, decision) -> Choice.single(decision, wanted::equals, wanted.toString());
    }

    /** Plays the first card in hand with this card id, on the given targets, one per choice slot. */
    public record PlayChoice(String card, List<Pick> targets) implements Choice {

        public PlayChoice {
            targets = List.copyOf(targets);
        }

        public PlayChoice on(Pick... picks) {
            return new PlayChoice(card, List.of(picks));
        }

        @Override
        public Action pick(GameState state, Decision decision) {
            List<TargetRef> wanted = targets.stream().map(pick -> pick.in(state)).toList();
            return Choice.single(decision, action -> action instanceof Action.PlayCard play
                    && isCard(state, play, card) && play.targets().equals(wanted), "play " + card);
        }

        private static boolean isCard(GameState state, Action.PlayCard play, String card) {
            return state.player(state.active()).handCard(play.card())
                    .map(HandCard::card)
                    .map(instance -> instance.card().equals(new CardId(card)))
                    .orElse(false);
        }
    }

    /** Attacks with the first unit with this card id, with its first attack ability unless told otherwise. */
    public record AttackChoice(String attacker, int attackIndex, Optional<Pick> target) implements Choice {

        public AttackChoice withAttack(int index) {
            return new AttackChoice(attacker, index, target);
        }

        public AttackChoice on(Pick newTarget) {
            return new AttackChoice(attacker, attackIndex, Optional.of(newTarget));
        }

        @Override
        public Action pick(GameState state, Decision decision) {
            Unit unit = Pick.nthUnit(state, attacker, 1);
            Optional<TargetRef> wanted = target.map(pick -> pick.in(state));
            return Choice.single(decision,
                    action -> action.equals(new Action.Attack(unit.id(), attackIndex, wanted)),
                    "attack with " + attacker);
        }
    }
}
