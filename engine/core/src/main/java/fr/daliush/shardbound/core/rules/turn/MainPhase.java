package fr.daliush.shardbound.core.rules.turn;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.combat.AttackOptions;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.play.PlayOptions;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.List;

/** 5.3: the active player plays cards and attacks in any order, then ends the turn. Always asked. */
public final class MainPhase {

    private MainPhase() {
    }

    public static void ask(Game game) {
        game.ask(game.active(), DecisionKind.MAIN, legalActions(game, game.active()));
    }

    /** Plays, then attacks, then ending the turn (spec §6.2). */
    public static List<Action> legalActions(Game game, PlayerId player) {
        List<Action> actions = new ArrayList<>(PlayOptions.list(game, player));
        actions.addAll(AttackOptions.list(game, player));
        actions.add(new Action.EndTurn());
        return actions;
    }

    public static void answer(Game game, PlayerId player, Action action) {
        switch (action) {
            case Action.PlayCard play -> game.push(new Step.ResolvePlay(player, play));
            case Action.Attack attack -> game.push(new Step.ResolveAttack(player,
                    game.unit(attack.attacker()).orElseThrow().asCard(), attack.attackIndex(), attack.target(),
                    Step.AttackPhase.DECLARE));
            case Action.EndTurn ignored -> game.push(new Step.TriggerTurnEnd(player));
            default -> throw new IllegalStateException(action + " is not a main-phase action");
        }
    }
}
