package fr.daliush.shardbound.dataset.recipe.echo;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.UnitSetup;
import fr.daliush.shardbound.dataset.recipe.Source;
import java.util.OptionalInt;

/** What the Echo recipes share: the events of a death and its echo, and how much damage kills. */
final class Echoes {

    private Echoes() {
    }

    /** A death, its echo, and the damage the echo deals. */
    static boolean isDeathOrEcho(GameEvent event) {
        return event instanceof GameEvent.UnitDestroyed || event instanceof GameEvent.EchoTriggered
                || event instanceof GameEvent.UnitDamaged || event instanceof GameEvent.PlayerDamaged;
    }

    /** 11.1.2: the damage the unit's echo deals, its printed damage at X%, rounded toward 0; 0 without one. */
    static int echoDamage(UnitCard unit) {
        return unit.attacks().stream()
                .filter(attack -> attack.echo().isPresent())
                .map(AttackAbility::echoEffects)
                .flatMap(effects -> effects.stream())
                .filter(Effect.Damage.class::isInstance)
                .mapToInt(effect -> ((Effect.Damage) effect).amount())
                .findFirst().orElse(0);
    }

    /** The defense that makes the source's damage lethal, if it deals damage: at most the unit's own. */
    static OptionalInt lethalDefense(Source source, UnitCard unit) {
        return source.effect() instanceof Effect.Damage damage
                ? OptionalInt.of(Math.min(damage.amount(), unit.defense()))
                : OptionalInt.empty();
    }

    static UnitSetup withDefense(UnitSetup unit, OptionalInt defense) {
        return defense.isPresent() ? unit.defense(defense.getAsInt()) : unit;
    }
}
