package fr.daliush.shardbound.core.testing;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.board.BoardSpace;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** What must hold after every step of every game (spec §16). */
public final class Invariants {

    private Invariants() {
    }

    public static void check(GameState state, Optional<Decision> decision, CardCatalog catalog) {
        for (PlayerId id : PlayerId.values()) {
            PlayerState player = state.player(id);
            assertThat(player.hp()).as("HP of %s", id).isLessThanOrEqualTo(PlayerState.MAX_HP);
            assertThat(player.hand()).as("hand of %s", id).hasSizeLessThanOrEqualTo(PlayerState.MAX_HAND);
            assertThat(BoardSpace.unitPlacesUsed(player, catalog)).as("units of %s", id)
                    .isLessThanOrEqualTo(PlayerState.MAX_UNIT_PLACES);
            assertThat(player.relics()).as("relics of %s", id).hasSizeLessThanOrEqualTo(PlayerState.MAX_RELICS);
            assertThat(player.shards().available()).as("Shards of %s", id).isNotNegative();
            assertThat(player.units()).allSatisfy(unit ->
                    assertThat(unit.defense()).isBetween(0, Math.max(0, unit.maxDefense())));
            assertThat(player.units()).as("doomed units of %s are at 0 defense (11.3.4)", id)
                    .allMatch(unit -> !unit.doomed() || unit.defense() == 0);
        }
        checkEveryCardIsInOneZone(state);
        checkDecision(state, decision);
    }

    private static void checkEveryCardIsInOneZone(GameState state) {
        List<InstanceId> ids = new ArrayList<>();
        int deckCards = 0;
        for (PlayerId id : PlayerId.values()) {
            PlayerState player = state.player(id);
            player.deck().forEach(card -> ids.add(card.id()));
            player.hand().stream().map(HandCard::id).forEach(ids::add);
            player.units().stream().map(Unit::id).forEach(ids::add);
            player.relics().stream().map(Relic::id).forEach(ids::add);
            player.graveyard().forEach(card -> ids.add(card.id()));
            deckCards += player.decklist().size();
        }
        // A spell waiting for a choice while it resolves is held by its resolution, between hand and graveyard.
        state.resolution().steps().stream()
                .filter(Step.FinishSpell.class::isInstance)
                .forEach(step -> ids.add(((Step.FinishSpell) step).spell().id()));
        Set<InstanceId> unique = new HashSet<>(ids);
        assertThat(unique).as("instance ids are unique").hasSameSizeAs(ids);
        if (state.isOver()) {
            // 1.6: a game can end while a spell resolves; the spell never reaches the graveyard.
            return;
        }
        for (int id = 1; id <= deckCards; id++) {
            assertThat(unique).as("card #%s is somewhere", id).contains(InstanceId.of(id));
        }
    }

    private static void checkDecision(GameState state, Optional<Decision> decision) {
        if (state.isOver()) {
            assertThat(decision).isEmpty();
            return;
        }
        assertThat(decision).as("a game in progress waits for a decision").isPresent();
        Decision pending = decision.orElseThrow();
        assertThat(pending.actions()).isNotEmpty();
        if (pending.kind() == DecisionKind.MAIN) {
            assertThat(pending.player()).as("only the active player gets MAIN (5.5)").isEqualTo(state.active());
        }
        if (pending.kind() == DecisionKind.INTERCEPT) {
            assertThat(pending.player()).as("the defender intercepts").isNotEqualTo(state.active());
        }
        if (pending.kind() != DecisionKind.MAIN && pending.kind() != DecisionKind.MULLIGAN) {
            assertThat(pending.actions()).as("asked only with a real choice").hasSizeGreaterThan(1);
        }
    }
}
