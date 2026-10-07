package fr.daliush.shardbound.core.state;

import fr.daliush.shardbound.core.content.CardId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A unit on the board. {@code defense} and {@code maxDefense} already include modifiers and auras; maluses can take the
 * max below 0 on a doomed unit (11.3.4), and its defense then stays at 0 (6.9).
 * {@code frozenThroughTurn} is 0 when not frozen; the unit is frozen while the turn is at most that number (8.10).
 */
public record Unit(
        InstanceId id,
        CardId card,
        PlayerId owner,
        PlayerId controller,
        boolean token,
        int arrivalSeq,
        int arrivedTurn,
        int defense,
        int maxDefense,
        List<Modifier> modifiers,
        List<AuraBonus> auras,
        boolean hasAttackedThisTurn,
        boolean hasInterceptedThisTurn,
        int frozenThroughTurn,
        boolean anchorProtected,
        boolean doomed,
        Optional<InstanceId> linkedTo) {

    public Unit {
        modifiers = List.copyOf(modifiers);
        auras = List.copyOf(auras);
    }

    /** A unit arriving on the board at full defense (6.5). */
    public static Unit arriving(CardInstance card, PlayerId controller, boolean token, int defense,
                                int arrivalSeq, int turn) {
        return new Unit(card.id(), card.card(), card.owner(), controller, token, arrivalSeq, turn, defense, defense,
                List.of(), List.of(), false, false, 0, false, false, Optional.empty());
    }

    public CardInstance asCard() {
        return new CardInstance(id, card, owner);
    }

    public boolean isFrozen(int turn) {
        return turn <= frozenThroughTurn;
    }

    public boolean arrivedOn(int turn) {
        return arrivedTurn == turn;
    }

    /** What modifiers and auras add to each damage effect of the unit's attack abilities (8.5, 8.18). */
    public int attackBonus() {
        return modifiers.stream().mapToInt(Modifier::attackDamage).sum()
                + auras.stream().mapToInt(AuraBonus::attackDamage).sum();
    }

    /** Defense never goes below 0 (6.9). */
    public Unit damaged(int amount) {
        return change(draft -> draft.defense = Math.max(0, defense - amount));
    }

    /** Up to the max defense (8.4); a heal never lowers defense. */
    public Unit healed(int amount) {
        return change(draft -> draft.defense = Math.max(defense, Math.min(maxDefense, defense + amount)));
    }

    public Unit withDefense(int value) {
        return change(draft -> draft.defense = value);
    }

    /** 8.5: +Y (or −Y) on both max and current defense at once; the attack part counts in {@link #attackBonus()}. */
    public Unit withModifier(Modifier modifier) {
        return change(draft -> {
            draft.modifiers.add(modifier);
            draft.startDefenseChange(modifier.defense());
        });
    }

    /** 8.5, 8.17: an ended bonus takes its max defense back and never kills; an ended malus gives back what it took. */
    public Unit withModifierEnded(Modifier modifier) {
        return change(draft -> {
            draft.modifiers.remove(modifier);
            draft.endDefenseChange(modifier.defense());
        });
    }

    /** 8.14: a stat aura's bonus starts like a Modify, so a unit arriving under it gets it in full (6.5). */
    public Unit withAura(AuraBonus bonus) {
        return change(draft -> {
            draft.auras.add(bonus);
            draft.startDefenseChange(bonus.defense());
        });
    }

    /** 8.14, 8.21: a stat aura that stops applying ends like an expiring modifier. */
    public Unit withAuraEnded(AuraBonus bonus) {
        return change(draft -> {
            draft.auras.remove(bonus);
            draft.endDefenseChange(bonus.defense());
        });
    }

    /** Frozen until the end of {@code turn} (8.10). */
    public Unit frozenThrough(int turn) {
        return change(draft -> draft.frozenThroughTurn = Math.max(frozenThroughTurn, turn));
    }

    /** 8.10: the turn after its freeze, the unit is no longer frozen. */
    public Unit thawed() {
        return change(draft -> draft.frozenThroughTurn = 0);
    }

    public Unit markHasAttacked() {
        return change(draft -> draft.hasAttackedThisTurn = true);
    }

    public Unit markHasIntercepted() {
        return change(draft -> draft.hasInterceptedThisTurn = true);
    }

    /** 11.3.1: an anchored unit is protected from its arrival. */
    public Unit withAnchorProtection() {
        return change(draft -> draft.anchorProtected = true);
    }

    /** 5.2.1: the protection ends at the start of its controller's next turn. */
    public Unit withAnchorProtectionEnded() {
        return change(draft -> draft.anchorProtected = false);
    }

    /** 11.3.4: at 0 defense, an anchored unit stays on the board, doomed. */
    public Unit markDoomed() {
        return change(draft -> draft.doomed = true);
    }

    /** 11.3.4: back above 0 defense, it is no longer doomed. */
    public Unit withDoomLifted() {
        return change(draft -> draft.doomed = false);
    }

    /** "This turn" in 7.2 and 7.5 starts over at every turn, the opponent's included. */
    public Unit withTurnFlagsCleared() {
        return change(draft -> {
            draft.hasAttackedThisTurn = false;
            draft.hasInterceptedThisTurn = false;
        });
    }

    private Unit change(Consumer<Draft> edit) {
        Draft draft = new Draft(this);
        edit.accept(draft);
        return draft.toUnit();
    }

    /** A mutable copy, so each change above only names the fields it touches. */
    private static final class Draft {
        private final Unit unit;
        int defense;
        int maxDefense;
        List<Modifier> modifiers;
        List<AuraBonus> auras;
        boolean hasAttackedThisTurn;
        boolean hasInterceptedThisTurn;
        int frozenThroughTurn;
        boolean anchorProtected;
        boolean doomed;
        Optional<InstanceId> linkedTo;

        Draft(Unit unit) {
            this.unit = unit;
            defense = unit.defense;
            maxDefense = unit.maxDefense;
            modifiers = new ArrayList<>(unit.modifiers);
            auras = new ArrayList<>(unit.auras);
            hasAttackedThisTurn = unit.hasAttackedThisTurn;
            hasInterceptedThisTurn = unit.hasInterceptedThisTurn;
            frozenThroughTurn = unit.frozenThroughTurn;
            anchorProtected = unit.anchorProtected;
            doomed = unit.doomed;
            linkedTo = unit.linkedTo;
        }

        /** Both defenses move. */
        void startDefenseChange(int change) {
            maxDefense += change;
            defense = bounded(defense + change);
        }

        /** A bonus ending lowers the max and caps the current defense (8.5); a malus ending gives both back (8.17). */
        void endDefenseChange(int change) {
            maxDefense -= change;
            defense = bounded(change > 0 ? defense : defense - change);
        }

        /** Never below 0 (6.9), never above the max. */
        private int bounded(int value) {
            return Math.max(0, Math.min(value, maxDefense));
        }

        Unit toUnit() {
            return new Unit(unit.id, unit.card, unit.owner, unit.controller, unit.token, unit.arrivalSeq,
                    unit.arrivedTurn, defense, maxDefense, modifiers, auras, hasAttackedThisTurn, hasInterceptedThisTurn,
                    frozenThroughTurn, anchorProtected, doomed, linkedTo);
        }
    }
}
