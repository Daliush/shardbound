package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The targets a player may choose for an effect, in canonical order (spec §6.2): units by arrival,
 * then relics by arrival, then players (the deciding player first).
 */
public final class TargetOptions {

    private TargetOptions() {
    }

    /** Empty when the effect has no chosen target, or when no target is valid (10.3). */
    public static List<TargetRef> forEffect(Game game, PlayerId decider, Effect effect) {
        if (effect instanceof Effect.Targeted targeted && targeted.target().isChosen()) {
            return forSpec(game, decider, targeted.target());
        }
        return List.of();
    }

    public static List<TargetRef> forSpec(Game game, PlayerId decider, TargetSpec spec) {
        PlayerId opponent = decider.opponent();
        return switch (spec) {
            case ALLY_UNIT -> units(game.player(decider).units().stream());
            case ENEMY_UNIT -> units(game.player(opponent).units().stream());
            case ANY_UNIT -> units(game.unitsByArrival().stream());
            case ANY_PLAYER -> List.of(TargetRef.player(decider), TargetRef.player(opponent));
            case ALLY_RELIC -> relics(game.player(decider).relics().stream());
            case ENEMY_RELIC -> relics(game.player(opponent).relics().stream());
            case ANY_RELIC -> relics(Stream.concat(game.player(decider).relics().stream(),
                    game.player(opponent).relics().stream()));
            default -> throw new IllegalArgumentException(spec + " is not chosen by a player");
        };
    }

    private static List<TargetRef> units(Stream<Unit> units) {
        return units.sorted(Comparator.comparingInt(Unit::arrivalSeq))
                .map(unit -> TargetRef.unit(unit.id()))
                .toList();
    }

    private static List<TargetRef> relics(Stream<Relic> relics) {
        return relics.sorted(Comparator.comparingInt(Relic::arrivalSeq))
                .<TargetRef>map(relic -> new TargetRef.RelicTarget(relic.id()))
                .toList();
    }
}
