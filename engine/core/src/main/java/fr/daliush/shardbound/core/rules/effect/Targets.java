package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/**
 * What an effect actually hits when it applies (section 10): chosen targets that are still valid,
 * random units, the card itself, groups, players or the attack's target.
 */
public final class Targets {

    private Targets() {
    }

    public static List<TargetRef> resolve(Game game, TargetSpec spec, EffectSource source, List<TargetRef> chosen) {
        PlayerId controller = source.controller();
        PlayerId opponent = controller.opponent();
        return switch (spec) {
            case ALLY_UNIT, ENEMY_UNIT, ANY_UNIT, ANY_PLAYER, ALLY_RELIC, ENEMY_RELIC, ANY_RELIC ->
                    stillValid(game, controller, spec, chosen);
            case RANDOM_ALLY_UNIT -> randomUnit(game, game.player(controller).units());
            case RANDOM_ENEMY_UNIT -> randomUnit(game, game.player(opponent).units());
            case SELF -> self(game, source);
            case ATTACK_TARGET -> source.attackTarget().filter(target -> isOnBoard(game, target)).stream().toList();
            case ALL_ALLY_UNITS -> units(game.player(controller).units());
            case ALL_ENEMY_UNITS -> units(game.player(opponent).units());
            case ALL_UNITS -> units(game.unitsByArrival());
            case YOU -> List.of(TargetRef.player(controller));
            case OPPONENT -> List.of(TargetRef.player(opponent));
        };
    }

    /** A chosen target that is no longer valid makes its effect do nothing (10.3, 10.5). */
    private static List<TargetRef> stillValid(Game game, PlayerId controller, TargetSpec spec, List<TargetRef> chosen) {
        List<TargetRef> valid = TargetOptions.forSpec(game, controller, spec);
        return chosen.stream().filter(valid::contains).toList();
    }

    private static List<TargetRef> randomUnit(Game game, List<Unit> units) {
        if (units.isEmpty()) {
            return List.of();
        }
        return List.of(TargetRef.unit(units.get(game.rng().nextInt(units.size())).id()));
    }

    private static List<TargetRef> self(Game game, EffectSource source) {
        if (game.unit(source.instance()).isPresent()) {
            return List.of(TargetRef.unit(source.instance()));
        }
        boolean relicOnBoard = game.player(source.controller()).relic(source.instance()).isPresent();
        return relicOnBoard ? List.of(new TargetRef.RelicTarget(source.instance())) : List.of();
    }

    private static List<TargetRef> units(List<Unit> units) {
        return units.stream().map(unit -> TargetRef.unit(unit.id())).toList();
    }

    private static boolean isOnBoard(Game game, TargetRef target) {
        return switch (target) {
            case TargetRef.UnitTarget unit -> game.unit(unit.id()).isPresent();
            case TargetRef.PlayerTarget ignored -> true;
            default -> false;
        };
    }
}
