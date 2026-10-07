package fr.daliush.shardbound.core.rules.combat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.rules.effect.Sacrifices;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.play.Costs;
import fr.daliush.shardbound.core.rules.play.EngineSupport;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Unit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Every legal {@code Attack} (7.2, 7.3, 8.16): attackers by arrival, then attack index, then targets. */
public final class AttackOptions {

    private AttackOptions() {
    }

    public static List<Action> list(Game game, PlayerId player) {
        PlayerState state = game.player(player);
        List<Action> attacks = new ArrayList<>();
        for (Unit unit : state.units().stream().sorted(Comparator.comparingInt(Unit::arrivalSeq)).toList()) {
            UnitCard card = game.catalog().unit(unit.card());
            if (!canAttack(game, unit) || !EngineSupport.supports(card)) {
                continue;
            }
            for (int index = 0; index < card.attacks().size(); index++) {
                AttackAbility attack = card.attacks().get(index);
                if (state.shards().canPay(Costs.toAttack(attack)) && canMakeItsSacrifices(game, state, card, attack)) {
                    attacks.addAll(withTargets(game, player, unit, index, attack));
                }
            }
        }
        return attacks;
    }

    /** 7.2: has not arrived this turn, is not frozen, has not attacked yet this turn. */
    private static boolean canAttack(Game game, Unit unit) {
        return !unit.arrivedOn(game.turn()) && !unit.isFrozen(game.turn()) && !unit.hasAttackedThisTurn();
    }

    /** 8.16: the Sacrifice effects of the attack ability and of the unit's "Attack" abilities; the attacker counts. */
    private static boolean canMakeItsSacrifices(Game game, PlayerState state, UnitCard card, AttackAbility attack) {
        int onAttack = card.abilities().stream()
                .filter(ability -> ability.trigger() == Trigger.ATTACK)
                .mapToInt(ability -> Sacrifices.askedBy(ability.effects()))
                .sum();
        return Sacrifices.canMake(state, game.catalog(), Sacrifices.askedBy(attack.effects()) + onAttack);
    }

    private static List<Action> withTargets(Game game, PlayerId player, Unit unit, int index, AttackAbility attack) {
        if (!attack.hasTarget()) {
            return List.of(new Action.Attack(unit.id(), index, Optional.empty()));
        }
        return targets(game, player).stream()
                .<Action>map(target -> new Action.Attack(unit.id(), index, Optional.of(target)))
                .toList();
    }

    /** 7.3: an enemy unit; the opposing player only when they have no unit. Never a relic. */
    public static List<TargetRef> targets(Game game, PlayerId attacker) {
        List<Unit> enemies = game.player(attacker.opponent()).units();
        if (enemies.isEmpty()) {
            return List.of(TargetRef.player(attacker.opponent()));
        }
        return enemies.stream()
                .sorted(Comparator.comparingInt(Unit::arrivalSeq))
                .map(unit -> TargetRef.unit(unit.id()))
                .toList();
    }
}
