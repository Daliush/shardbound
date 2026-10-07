package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.testing.TestContent;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Every effect and keyword is in the engine (spec §17, slice 4), so every real card can be played, and every attack
 * ability used, on a board that gives it what it needs.
 */
class PlayableCardsTest {

    @Test
    void everyCardCanBePlayed() {
        for (CardDefinition card : TestContent.catalog().all()) {
            if (card.isToken()) {
                continue;
            }
            GameState start = board().hand(P1, card.id().value()).build();
            InstanceId inHand = start.p1().hand().getFirst().id();

            assertThat(main(start).actions()).as("%s is playable", card.id())
                    .anyMatch(action -> action instanceof Action.PlayCard play && play.card().equals(inHand));
        }
    }

    @Test
    void everyAttackAbilityCanBeUsed() {
        for (CardDefinition card : TestContent.catalog().all()) {
            if (!(card instanceof UnitCard unit)) {
                continue;
            }
            GameState start = board().unit(P1, unit.id().value()).build();
            InstanceId attacker = start.p1().units().getLast().id();

            assertThat(IntStream.range(0, unit.attacks().size())).as("attacks of %s", card.id())
                    .allMatch(index -> main(start).actions().stream().anyMatch(action ->
                            action instanceof Action.Attack attack && attack.attacker().equals(attacker)
                                    && attack.attackIndex() == index));
        }
    }

    /** Shards, units on both sides, a relic, a unit card in the graveyard and cards to draw. */
    private static ScenarioBuilder board() {
        return scenario().shards(P1, 10).unit(P1, "neutral.shardling").unit(P1, "neutral.shardling")
                .unit(P2, "neutral.shardling").unit(P2, "neutral.shardling").relic(P2, "root.heartwood-shrine")
                .graveyard(P1, "ember.cinderling").deck(P1, "neutral.shardling", "neutral.shardling");
    }

    private static Decision main(GameState start) {
        return ENGINE.resume(start).state().pending().orElseThrow();
    }
}
