package fr.daliush.shardbound.core.rules;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;
import static fr.daliush.shardbound.core.scenario.Choices.play;
import static fr.daliush.shardbound.core.scenario.Pick.unit;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static fr.daliush.shardbound.core.testing.RuleTesting.ENGINE;
import static fr.daliush.shardbound.core.testing.RuleTesting.run;
import static fr.daliush.shardbound.core.testing.RuleTesting.scenario;
import static fr.daliush.shardbound.core.testing.RuleTesting.trace;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.text.EventDescriber;
import fr.daliush.shardbound.core.testing.TestCards;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook 11.2: Fracture. */
class FractureRulesTest {

    private static final CardId MOONPULL = new CardId("tide.moonpull");

    @Test
    @DisplayName("11.2.1 — each Fracture step has its own cost, possibly 0, and its own effect")
    void stepCosts() {
        ScenarioResult secondStep = run(scenario().shards(P1, 2).handAtStep(P1, "tide.moonpull", 2)
                        .unit(P2, "neutral.shard-construct").build(),
                play("tide.moonpull").on(unit("neutral.shard-construct")));
        ScenarioResult freeStep = run(scenario().hand(P1, "tide.spring-tide").deck(P2, "neutral.shardling").build(),
                play("tide.spring-tide"));

        assertThat(secondStep.events(GameEvent.CardPlayed.class).getFirst().cost()).isEqualTo(2);
        assertThat(secondStep.unit("neutral.shard-construct").defense()).isEqualTo(6);
        assertThat(freeStep.events(GameEvent.CardPlayed.class).getFirst().cost()).isZero();
        assertThat(freeStep.player(P2).hand()).hasSize(1);
    }

    @Test
    @DisplayName("11.2.2 — playing a Fracture card applies its next step, then it returns to its owner's hand")
    void returnsToHand() {
        GameState start = scenario().shards(P1, 1).hand(P1, "tide.moonpull").unit(P1, "neutral.shardling").build();

        ScenarioResult result = run(start, play("tide.moonpull").on(unit("neutral.shardling")));

        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3, 11.2.2]", "Modified[8.5]",
                "FractureAdvanced[11.2.2]");
        assertThat(result.events(GameEvent.CardPlayed.class).getFirst().fractureStep()).hasValue(1);
        HandCard back = result.player(P1).hand().getFirst();
        assertThat(back.id()).isEqualTo(start.p1().hand().getFirst().id());
        assertThat(back.fractureStep()).as("0-based: step 2 is next").isEqualTo(1);
        assertThat(result.player(P1).graveyard()).isEmpty();
    }

    @Test
    @DisplayName("11.2.3 — two steps of one card are never played in the same turn, but there is no deadline")
    void oneStepPerTurn() {
        GameState start = scenario().shards(P1, 3).hand(P1, "tide.moonpull").unit(P1, "neutral.shardling")
                .unit(P2, "root.sprout").deck(P1, "neutral.shardling", "neutral.shardling")
                .deck(P2, "neutral.shardling", "neutral.shardling").build();

        ScenarioResult sameTurn = run(start, play("tide.moonpull").on(unit("neutral.shardling")));
        ScenarioResult twoTurnsLater = run(start, play("tide.moonpull").on(unit("neutral.shardling")),
                endTurn(), endTurn(), endTurn(), endTurn());

        assertThat(sameTurn.pending().orElseThrow().actions()).noneMatch(Action.PlayCard.class::isInstance);
        assertThat(twoTurnsLater.pending().orElseThrow().actions()).anyMatch(action ->
                action instanceof Action.PlayCard play
                        && play.card().equals(start.p1().hand().getFirst().id()));
    }

    @Test
    @DisplayName("11.2.4 — after its last step, a Fracture card goes to the graveyard")
    void lastStep() {
        ScenarioResult result = run(scenario().shards(P1, 3).handAtStep(P1, "tide.moonpull", 3)
                        .unit(P2, "root.sprout").build(),
                play("tide.moonpull"));

        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3, 11.2.2]", "Frozen[8.10]",
                "SpellResolved[6.3, 3.5, 11.2.4]");
        assertThat(result.player(P1).hand()).isEmpty();
        assertThat(result.player(P1).graveyard()).extracting(CardInstance::card).containsExactly(MOONPULL);
    }

    @Test
    @DisplayName("11.2.5 — the step played is public, but only its owner's view and log show it")
    void stepsArePublicButNotShown() {
        ScenarioResult result = run(scenario().shards(P1, 1).hand(P1, "tide.moonpull").unit(P1, "neutral.shardling")
                        .build(),
                play("tide.moonpull").on(unit("neutral.shardling")));
        EventDescriber describer = new EventDescriber(TestCards.CATALOG);
        List<GameEvent> seenByOpponent = ENGINE.eventsFor(result.events(), P2);
        GameEvent played = seenByOpponent.stream().filter(GameEvent.CardPlayed.class::isInstance).findFirst()
                .orElseThrow();
        GameEvent advanced = seenByOpponent.stream().filter(GameEvent.FractureAdvanced.class::isInstance).findFirst()
                .orElseThrow();
        PlayerView opponent = ENGINE.view(result.state(), P2, result.events());

        assertThat(((GameEvent.CardPlayed) played).fractureStep()).hasValue(1);
        assertThat(describer.describe(played, P2)).isEqualTo("Your opponent plays Moonpull (1 Shard).");
        assertThat(describer.describe(played, P1)).isEqualTo("You play Moonpull, step 1 (1 Shard).");
        assertThat(describer.describe(advanced, P2)).isEqualTo("Moonpull #1 returns to your opponent's hand.");
        assertThat(describer.describe(advanced, P1)).isEqualTo("Moonpull #1 returns to your hand, step 2.");
        assertThat(opponent.opponent().handCount()).isEqualTo(1);
        assertThat(ENGINE.view(result.state(), P1, result.events()).self().hand().getFirst().fractureStep())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("11.2.8 — a Fracture card with a sacrifice cost pays it at each step")
    void sacrificeAtEachStep() {
        GameState start = scenario().shards(P1, 1).hand(P1, "test.blood-tide")
                .unit(P1, "neutral.shardling").unit(P1, "neutral.shardling")
                .deck(P1, "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling")
                .deck(P2, "neutral.shardling").build();

        ScenarioResult result = run(start, play("test.blood-tide").sacrificing(unit("neutral.shardling")), endTurn(),
                endTurn(), play("test.blood-tide").sacrificing(unit("neutral.shardling")));

        assertThat(result.events(GameEvent.UnitSacrificed.class)).hasSize(2);
        assertThat(trace(result)).containsSubsequence("CardPlayed[6.3, 11.2.2]", "UnitSacrificed[6.3, 8.3]",
                "FractureAdvanced[11.2.2]", "CardPlayed[6.3, 11.2.2]", "UnitSacrificed[6.3, 8.3]",
                "SpellResolved[6.3, 3.5, 11.2.4]");
    }

    @Test
    @DisplayName("11.2.6 — a Fracture card discarded between two steps loses its progress")
    void discardLosesProgress() {
        ScenarioResult result = run(scenario().shards(P1, 2).hand(P1, "tide.brinesong").deck(P1, "neutral.shardling")
                        .handAtStep(P2, "tide.moonpull", 2).build(),
                play("tide.brinesong"));

        assertThat(trace(result)).contains("CardDiscarded[8.7, 11.2.6]");
        assertThat(result.player(P2).hand()).isEmpty();
        assertThat(result.player(P2).graveyard()).extracting(CardInstance::card).containsExactly(MOONPULL);
    }

    @Test
    @DisplayName("11.2.7 — a Fracture card that would return to a full hand goes to the graveyard instead")
    void fullHand() {
        ScenarioResult result = run(scenario().shards(P1, 1).handAtStep(P1, "tide.spring-tide", 2)
                        .hand(P1, "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                                "neutral.shardling", "neutral.shardling", "neutral.shardling", "neutral.shardling",
                                "neutral.shardling")
                        .deck(P1, "neutral.shardling").build(),
                play("tide.spring-tide"));

        assertThat(trace(result)).containsSubsequence("CardDrawn[8.6]", "SentToGraveyardHandFull[11.2.7, 3.3]");
        assertThat(result.player(P1).hand()).hasSize(10);
        assertThat(result.player(P1).graveyard()).extracting(CardInstance::card)
                .containsExactly(new CardId("tide.spring-tide"));
    }
}
