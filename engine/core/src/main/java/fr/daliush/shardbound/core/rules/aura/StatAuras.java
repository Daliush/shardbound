package fr.daliush.shardbound.core.rules.aura;

import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.AuraBonus;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 8.14: every unit gets the bonus of each stat aura that reaches it, no more and no less. A bonus that starts moves
 * both defenses like a Modify (6.5); one that stops ends like an expiring modifier (8.5, 8.21).
 */
public final class StatAuras {

    private StatAuras() {
    }

    /** Run by every state check, so units always carry exactly what the board gives them. */
    public static void reconcile(Game game) {
        List<ActiveAura> auras = ActiveAura.on(game.catalog(), game.player(PlayerId.P1), game.player(PlayerId.P2))
                .stream()
                .filter(aura -> aura.aura() instanceof Effect.StatAura)
                .toList();
        for (Unit unit : game.unitsByArrival()) {
            List<AuraBonus> wanted = wanted(unit, auras);
            if (!(wanted.containsAll(unit.auras()) && unit.auras().containsAll(wanted))) {
                game.updateUnit(reapplied(game, unit, wanted));
            }
        }
    }

    /** What each aura card gives the unit, its stat auras added up, from the card that arrived first. */
    private static List<AuraBonus> wanted(Unit unit, List<ActiveAura> auras) {
        Map<CardInstance, AuraBonus> bySource = new LinkedHashMap<>();
        for (ActiveAura active : auras) {
            Effect.StatAura stats = (Effect.StatAura) active.aura();
            if (reaches(stats.target(), active.controller(), unit)) {
                bySource.merge(active.source(), new AuraBonus(active.source(), stats.attackDamage(), stats.defense()),
                        (sum, more) -> new AuraBonus(sum.source(), sum.attackDamage() + more.attackDamage(),
                                sum.defense() + more.defense()));
            }
        }
        return List.copyOf(bySource.values());
    }

    private static boolean reaches(TargetSpec group, PlayerId auraController, Unit unit) {
        return switch (group) {
            case ALL_ALLY_UNITS -> unit.controller() == auraController;
            case ALL_ENEMY_UNITS -> unit.controller() != auraController;
            case ALL_UNITS -> true;
            default -> throw new IllegalStateException(group + " is not a group of units");
        };
    }

    private static Unit reapplied(Game game, Unit unit, List<AuraBonus> wanted) {
        Unit changed = unit;
        for (AuraBonus applied : unit.auras()) {
            if (!wanted.contains(applied)) {
                changed = changed.withAuraEnded(applied);
                game.emit(new GameEvent.AuraRemoved(unit.asCard(), applied.source(), applied.attackDamage(),
                        applied.defense()));
            }
        }
        for (AuraBonus bonus : wanted) {
            if (!unit.auras().contains(bonus)) {
                changed = changed.withAura(bonus);
                game.emit(new GameEvent.AuraApplied(unit.asCard(), bonus.source(), bonus.attackDamage(),
                        bonus.defense()));
            }
        }
        return changed;
    }
}
