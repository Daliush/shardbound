package fr.daliush.shardbound.api.domain.bo.view;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public record UnitView(
        int id,
        String card,
        String controller,
        boolean token,
        int defense,
        int maxDefense,
        List<AttackView> attacks,
        boolean arrivedThisTurn,
        boolean hasAttackedThisTurn,
        boolean hasInterceptedThisTurn,
        boolean frozen,
        boolean anchorProtected,
        boolean doomed,
        OptionalInt linkedTo,
        List<ModifierView> modifiers) {

    /** {@code damage} is what the attack deals to its target, bonuses included; empty when it deals none. */
    public record AttackView(int index, Optional<String> name, int cost, OptionalInt damage, boolean hasTarget,
                             OptionalInt echo) {
    }

    public record ModifierView(int attackDamage, int defense, String duration) {
    }
}
