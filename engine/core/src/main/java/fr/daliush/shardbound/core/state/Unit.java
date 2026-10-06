package fr.daliush.shardbound.core.state;

import fr.daliush.shardbound.core.content.CardId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A unit on the board. {@code defense} and {@code maxDefense} already include modifiers and auras.
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
        boolean attackedThisTurn,
        boolean interceptedThisTurn,
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

    public Unit healed(int amount) {
        return change(draft -> draft.defense = Math.min(maxDefense, defense + amount));
    }

    public Unit withDefense(int value) {
        return change(draft -> draft.defense = value);
    }

    /** Frozen until the end of {@code turn} (8.10). */
    public Unit frozenThrough(int turn) {
        return change(draft -> draft.frozenThroughTurn = Math.max(frozenThroughTurn, turn));
    }

    public Unit markAttacked() {
        return change(draft -> draft.attackedThisTurn = true);
    }

    public Unit markIntercepted() {
        return change(draft -> draft.interceptedThisTurn = true);
    }

    /** "This turn" in 7.2 and 7.5 starts over at every turn, the opponent's included. */
    public Unit withTurnFlagsCleared() {
        return change(draft -> {
            draft.attackedThisTurn = false;
            draft.interceptedThisTurn = false;
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
        boolean attackedThisTurn;
        boolean interceptedThisTurn;
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
            attackedThisTurn = unit.attackedThisTurn;
            interceptedThisTurn = unit.interceptedThisTurn;
            frozenThroughTurn = unit.frozenThroughTurn;
            anchorProtected = unit.anchorProtected;
            doomed = unit.doomed;
            linkedTo = unit.linkedTo;
        }

        Unit toUnit() {
            return new Unit(unit.id, unit.card, unit.owner, unit.controller, unit.token, unit.arrivalSeq,
                    unit.arrivedTurn, defense, maxDefense, modifiers, auras, attackedThisTurn, interceptedThisTurn,
                    frozenThroughTurn, anchorProtected, doomed, linkedTo);
        }
    }
}
