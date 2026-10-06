package fr.daliush.shardbound.core.rules.combat;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.Unit;
import fr.daliush.shardbound.core.testing.TestCards;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class AttackDamageTest {

    @Test
    void countsOnlyTheDamageDealtToTheAttacksTarget() {
        GameState state = scenario().unit(P1, "ember.cinderling").unit(P1, "root.mossmender")
                .unit(P1, "tide.brine-adept").build();

        assertThat(damage(state, "ember.cinderling", 0)).isEqualTo(OptionalInt.of(3));
        assertThat(damage(state, "root.mossmender", 0)).isEqualTo(OptionalInt.of(4));
        assertThat(damage(state, "root.mossmender", 1)).isEmpty();
        assertThat(damage(state, "tide.brine-adept", 0)).isEqualTo(OptionalInt.of(2));
    }

    @Test
    void aBonusAddsToTheDamageWhichNeverGoesBelowZero() {
        Effect.Damage hit = new Effect.Damage(3, TargetSpec.ATTACK_TARGET);

        assertThat(hit.withBonus(2)).isEqualTo(5);
        assertThat(hit.withBonus(-5)).isZero();
    }

    private static OptionalInt damage(GameState state, String card, int attackIndex) {
        Unit unit = state.unitsByArrival().stream().filter(u -> u.card().value().equals(card)).findFirst()
                .orElseThrow();
        return AttackDamage.toTarget(TestCards.CATALOG.unit(unit.card()).attacks().get(attackIndex), unit);
    }
}
