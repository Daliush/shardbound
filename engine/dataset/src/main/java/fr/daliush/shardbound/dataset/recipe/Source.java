package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.scenario.Choice;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.Pick;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Locale;

/**
 * What applies the effect a recipe looks at: a spell cast from hand, or the attack ability of a unit on the board.
 * {@code effect} is that effect, the first of its kind on the card.
 */
public sealed interface Source {

    /** "neutral.crystal-rupture", "tide.deepcurrent-behemoth:engulf": a part of a scenario's id. */
    String id();

    Effect.Targeted effect();

    default TargetSpec target() {
        return effect().target();
    }

    /** The Shards it costs: the spell's cost, or the attack ability's. */
    int cost();

    /** Puts it where it is played from: the spell in the caster's hand, or the attacker on their board. */
    ScenarioBuilder place(ScenarioBuilder board, PlayerId caster);

    /** Places it and gives the caster exactly the Shards it costs. */
    default ScenarioBuilder prepare(ScenarioBuilder board, PlayerId caster) {
        return place(board, caster).shards(caster, cost());
    }

    /** The choice that plays it, aimed at the first unit with card {@code unitCard} when its target is chosen. */
    Choice play(String unitCard);

    /** "cast Crystal Rupture on it", "attack it with Deepcurrent Behemoth's Engulf", for the unit {@code unitName}. */
    String phrase(String unitName);

    record Spell(SpellCard card, Effect.Targeted effect) implements Source {

        @Override
        public String id() {
            return card.id().value();
        }

        @Override
        public int cost() {
            return card.cost().orElseThrow();
        }

        @Override
        public ScenarioBuilder place(ScenarioBuilder board, PlayerId caster) {
            return board.hand(caster, id());
        }

        @Override
        public Choice play(String unitCard) {
            return target().isChosen() ? Choices.play(id()).on(Pick.unit(unitCard)) : Choices.play(id());
        }

        @Override
        public String phrase(String unitName) {
            return "cast " + card.name() + (target().isChosen() ? " on " + unitName : "");
        }
    }

    record Attack(UnitCard unit, int index, Effect.Targeted effect) implements Source {

        @Override
        public String id() {
            return unit.id().value() + ":" + name().toLowerCase(Locale.ROOT).replace(' ', '-');
        }

        @Override
        public int cost() {
            return ability().cost();
        }

        @Override
        public ScenarioBuilder place(ScenarioBuilder board, PlayerId caster) {
            return board.unit(caster, unit.id().value());
        }

        @Override
        public Choice play(String unitCard) {
            Choices.AttackChoice attack = Choices.attack(unit.id().value()).withAttack(index);
            return ability().hasTarget() ? attack.on(Pick.unit(unitCard)) : attack;
        }

        @Override
        public String phrase(String unitName) {
            String with = unit.name() + "'s " + name();
            return ability().hasTarget() ? "attack " + unitName + " with " + with : "use " + with;
        }

        private AttackAbility ability() {
            return unit.attacks().get(index);
        }

        private String name() {
            return ability().name().orElse("attack " + (index + 1));
        }
    }
}
