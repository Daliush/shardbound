package fr.daliush.shardbound.core.bot.greedy;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.rules.combat.AttackDamage;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Unit;

/**
 * How good a game is for one player (spec §10), the starting formula:
 * {@code (myHp − oppHp) + Σ my units (defense + 2 × best attack damage) − Σ enemy units (same)
 * + 3 × (my unit count − enemy unit count) + 0.5 × my hand size}. A win scores +∞, a loss −∞ and a draw 0.
 */
final class Evaluator {

    static final double ATTACK_DAMAGE_WEIGHT = 2;
    static final double UNIT_COUNT_WEIGHT = 3;
    static final double HAND_CARD_WEIGHT = 0.5;

    private final CardCatalog catalog;

    Evaluator(CardCatalog catalog) {
        this.catalog = catalog;
    }

    double score(GameState state, PlayerId player) {
        if (state.result().isPresent()) {
            return switch (state.result().get()) {
                case GameResult.Win win -> win.winner() == player ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                case GameResult.Draw draw -> 0;
            };
        }
        PlayerState mine = state.player(player);
        PlayerState theirs = state.player(player.opponent());
        return (mine.hp() - theirs.hp())
                + board(mine) - board(theirs)
                + UNIT_COUNT_WEIGHT * (mine.units().size() - theirs.units().size())
                + HAND_CARD_WEIGHT * mine.hand().size();
    }

    private double board(PlayerState player) {
        return player.units().stream().mapToDouble(unit -> unit.defense() + ATTACK_DAMAGE_WEIGHT * bestAttackDamage(unit))
                .sum();
    }

    /** The most an attack of the unit deals to its target, its bonuses included (8.5, 8.18). */
    private int bestAttackDamage(Unit unit) {
        return catalog.unit(unit.card()).attacks().stream()
                .mapToInt(attack -> AttackDamage.toTarget(attack, unit).orElse(0))
                .max()
                .orElse(0);
    }
}
