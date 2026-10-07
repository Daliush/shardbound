package fr.daliush.shardbound.core.text;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.resolution.EffectList;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.combat.AttackDamage;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.Unit;

/**
 * The question a decision asks, from the deciding player's point of view:
 * "Cinderling #1 attacks your Root Sentinel #36 with Flick (3 damage). Intercept with another unit?"
 */
public final class DecisionDescriber {

    private final CardCatalog catalog;

    public DecisionDescriber(CardCatalog catalog) {
        this.catalog = catalog;
    }

    public String describe(Decision decision, GameState state) {
        Wording w = new Wording(catalog, decision.player());
        return switch (decision.kind()) {
            case MULLIGAN -> "Keep your opening hand, or shuffle it back and draw 4?";
            case MAIN -> "Your turn: play a card, attack, or end your turn.";
            case INTERCEPT -> intercept(w, pausedStep(state, Step.ResolveAttack.class), state);
            case CHOOSE_TARGET -> "Choose a target for " + source(w, pausedStep(state, Step.ChooseTargets.class).source())
                    + ".";
            case CHOOSE_CARDS -> "Choose the cards.";
            case CHOOSE_ORDER -> "Choose the order.";
        };
    }

    private String intercept(Wording w, Step.ResolveAttack attack, GameState state) {
        Unit attacker = unit(state, attack.attacker().id());
        if (!(attack.target().orElseThrow() instanceof TargetRef.UnitTarget aimedAt)) {
            throw new IllegalStateException("Only an attack on a unit can be intercepted: " + attack);
        }
        Unit target = unit(state, aimedAt.id());
        AttackAbility ability = catalog.unit(attacker.card()).attacks().get(attack.attackIndex());
        String damage = AttackDamage.toTarget(ability, attacker).stream()
                .mapToObj(amount -> " (" + amount + " damage)")
                .findFirst()
                .orElse("");
        return w.card(attacker.asCard()) + " attacks " + w.possessive(target.controller()) + " "
                + w.card(target.asCard()) + " with " + ability.name().orElse("its attack") + damage
                + ". Intercept with another unit?";
    }

    /** "the Arrival ability of Watcher #5", "Cinder Bite of Ash Warden #15", "Spark Dart #3". */
    private String source(Wording w, EffectSource source) {
        String card = w.card(source.effects().card(), source.instance());
        return switch (source.effects()) {
            case EffectList.SpellEffects ignored -> card;
            case EffectList.AttackEffects attack -> catalog.unit(attack.card()).attacks().get(attack.attackIndex())
                    .name().orElse("the attack") + " of " + card;
            case EffectList.AbilityEffects ability -> "the " + Wording.trigger(Triggers.abilitiesOf(
                    catalog.card(ability.card())).get(ability.abilityIndex()).trigger()) + " ability of " + card;
        };
    }

    /** A decision asked in the middle of a step keeps that step at the front of the pending work. */
    private static <S extends Step> S pausedStep(GameState state, Class<S> type) {
        Step front = state.resolution().steps().getFirst();
        if (!type.isInstance(front)) {
            throw new IllegalStateException("Expected a paused " + type.getSimpleName() + ", found " + front);
        }
        return type.cast(front);
    }

    private static Unit unit(GameState state, InstanceId id) {
        return state.unit(id).orElseThrow(() -> new IllegalStateException("No unit " + id));
    }
}
