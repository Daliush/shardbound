package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.attack;
import static fr.daliush.shardbound.core.scenario.Choices.declineIntercept;
import static fr.daliush.shardbound.core.scenario.Choices.interceptWith;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.player;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.GameState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook section 7: attacking, targets, intercept. */
class CombatRulesTest {

    @Test
    @DisplayName("7.1 — a unit attacks with one of its one or two attack abilities")
    void attackAbilities() {
        List<Action.Attack> attacks = attacks(scenario().shards(P1, 3).unit(P1, "root.thornback-ancient").build());

        assertThat(attacks).extracting(Action.Attack::attackIndex).containsExactly(0, 1);
    }

    @Test
    @DisplayName("7.2 — a unit cannot attack on the turn it arrived")
    void summoningSickness() {
        assertThat(attacks(scenario().shards(P1, 3)
                .unit(P1, "neutral.shardling", unit -> unit.arrivedThisTurn()).build())).isEmpty();
    }

    @Test
    @DisplayName("7.2 — a unit attacks once per turn, even with two attack abilities")
    void oncePerTurn() {
        ScenarioResult result = run(scenario().shards(P1, 5).unit(P1, "root.thornback-ancient").build(),
                attack("root.thornback-ancient").withAttack(1));

        assertThat(result.pending().orElseThrow().actions()).noneMatch(Action.Attack.class::isInstance);
        assertThat(result.unit("root.thornback-ancient").attackedThisTurn()).isTrue();
    }

    @Test
    @DisplayName("7.2 — a frozen unit cannot attack")
    void frozenCannotAttack() {
        assertThat(attacks(scenario().turn(3).shards(P1, 3)
                .unit(P1, "neutral.shardling", unit -> unit.frozenThroughTurn(3)).build())).isEmpty();
    }

    @Test
    @DisplayName("7.3 — an attack targets an enemy unit; the opposing player only when they have none")
    void attackTargets() {
        GameState withUnits = scenario().shards(P1, 1).unit(P1, "neutral.shardling")
                .unit(P2, "root.sprout").unit(P2, "neutral.shardling").relic(P2, "root.heartwood-shrine").build();
        GameState empty = scenario().shards(P1, 1).unit(P1, "neutral.shardling").build();

        assertThat(attacks(withUnits)).extracting(attack -> attack.target().orElseThrow())
                .allMatch(TargetRef.UnitTarget.class::isInstance).hasSize(2);
        assertThat(attacks(empty)).extracting(attack -> attack.target().orElseThrow())
                .containsExactly(TargetRef.player(P2));
    }

    @Test
    @DisplayName("7.4 — an attack pays its cost, triggers, may be intercepted, then applies to its target")
    void attackSequence() {
        ScenarioResult result = run(scenario().shards(P1, 2).unit(P1, "ember.cinderling")
                        .unit(P2, "neutral.shard-construct").build(),
                attack("ember.cinderling").on(unit("neutral.shard-construct")));

        assertThat(result.player(P1).shards().available()).isEqualTo(1);
        assertThat(trace(result)).containsSubsequence("AttackDeclared[7.4]", "UnitDamaged[8.1]");
        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(6);
    }

    @Test
    @DisplayName("7.5 — the defender may redirect the attack to another of their units")
    void intercept() {
        ScenarioResult result = run(scenario().shards(P1, 1).unit(P1, "ember.cinderling")
                        .unit(P2, "neutral.shard-construct").unit(P2, "neutral.shardling").build(),
                attack("ember.cinderling").on(unit("neutral.shard-construct")),
                interceptWith("neutral.shardling"));

        assertThat(result.decisions().get(1).kind()).isEqualTo(DecisionKind.INTERCEPT);
        assertThat(result.decisions().get(1).player()).isEqualTo(P2);
        assertThat(trace(result)).containsSubsequence("AttackIntercepted[7.5]", "UnitDamaged[8.1]");
        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(9);
        assertThat(result.findUnit("neutral.shardling")).isEmpty();
    }

    @Test
    @DisplayName("7.5 — declining an intercept leaves the attack on its target")
    void declinedIntercept() {
        ScenarioResult result = run(scenario().shards(P1, 1).unit(P1, "ember.cinderling")
                        .unit(P2, "neutral.shard-construct").unit(P2, "neutral.shardling").build(),
                attack("ember.cinderling").on(unit("neutral.shard-construct")),
                declineIntercept());

        assertThat(trace(result)).contains("InterceptDeclined[7.5]");
        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(6);
    }

    @Test
    @DisplayName("7.5 — a unit that already intercepted this turn, or a frozen unit, cannot intercept")
    void interceptLimits() {
        ScenarioResult result = run(scenario().turn(3).shards(P1, 1).unit(P1, "ember.cinderling")
                        .unit(P2, "neutral.shard-construct")
                        .unit(P2, "neutral.shardling", unit -> unit.intercepted())
                        .unit(P2, "root.sprout", unit -> unit.frozenThroughTurn(3)).build(),
                attack("ember.cinderling").on(unit("neutral.shard-construct")));

        assertThat(result.decisions()).hasSize(1);
        assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(6);
    }

    @Test
    @DisplayName("7.6 — no retaliation: the target deals nothing back")
    void noRetaliation() {
        ScenarioResult result = run(scenario().shards(P1, 3).unit(P1, "neutral.shardling")
                        .unit(P2, "root.thornback-ancient").build(),
                attack("neutral.shardling").on(unit("root.thornback-ancient")));

        assertThat(result.unit("neutral.shardling").defense()).isEqualTo(3);
    }

    @Test
    @DisplayName("7.7 — a spell can hit the opposing player even when they have units")
    void spellsIgnoreAttackTargeting() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "test.bolt")
                        .unit(P2, "neutral.shard-construct").build(),
                play("test.bolt"));

        assertThat(result.player(P2).hp()).isEqualTo(47);
    }

    @Test
    @DisplayName("7.8 — an attack without a target cannot be intercepted but still counts as the unit's attack")
    void attackWithoutTarget() {
        ScenarioResult result = run(scenario().shards(P1, 1).unit(P1, "root.mossmender", unit -> unit.defense(2))
                        .unit(P2, "neutral.shardling").unit(P2, "root.sprout").build(),
                attack("root.mossmender").withAttack(1));

        assertThat(result.decisions()).hasSize(1);
        assertThat(result.unit("root.mossmender").defense()).isEqualTo(4);
        assertThat(result.unit("root.mossmender").attackedThisTurn()).isTrue();
    }

    @Test
    @DisplayName("7.9 — an attack whose target left the board still happens: only the effects on the target do nothing")
    void targetGone() {
        ScenarioResult result = run(scenario().hp(P1, 40).shards(P1, 1).unit(P1, "test.charger")
                        .unit(P2, "root.sprout").unit(P2, "neutral.shardling").build(),
                attack("test.charger").on(unit("root.sprout")));

        assertThat(result.decisions()).hasSize(1);
        assertThat(result.events(GameEvent.UnitDamaged.class)).hasSize(2);
        assertThat(result.player(P1).hp()).isEqualTo(41);
        assertThat(result.unit("test.charger").attackedThisTurn()).isTrue();
    }

    @Test
    @DisplayName("7.10 — an attacker that left the board does not attack; its cost stays paid")
    void attackerGone() {
        ScenarioResult result = run(scenario().shards(P1, 1).unit(P1, "test.reckless")
                        .unit(P2, "neutral.shardling").build(),
                attack("test.reckless").on(unit("neutral.shardling")));

        assertThat(trace(result)).containsSubsequence("AttackDeclared[7.4]", "UnitDestroyed[8.2]",
                "AttackCancelled[7.10]");
        assertThat(result.unit("neutral.shardling").defense()).isEqualTo(3);
        assertThat(result.player(P1).shards().available()).isZero();
    }

    @Test
    void attackingThePlayerDealsDamageToThem() {
        ScenarioResult result = run(scenario().shards(P1, 1).unit(P1, "ember.cinderling").build(),
                attack("ember.cinderling").on(player(P2)));

        assertThat(result.player(P2).hp()).isEqualTo(47);
    }

    private static List<Action.Attack> attacks(GameState start) {
        return ENGINE.resume(start).state().pending().orElseThrow().actions().stream()
                .filter(Action.Attack.class::isInstance).map(Action.Attack.class::cast).toList();
    }
}
