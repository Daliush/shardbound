package fr.daliush.shardbound.core.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.testing.TestContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rulebook sections 2 and 5.1: decks and the setup of a game. */
class SetupRulesTest {

    private final Content content = TestContent.content();
    private final GameEngine engine = new GameEngine(content.catalog());

    @Test
    @DisplayName("2.1 — a new game refuses an illegal deck")
    void refusesIllegalDeck() {
        Deck ember = content.deck("ember-starter");
        Deck tooSmall = new Deck(ember.id(), ember.name(), Optional.empty(), ember.faction(),
                ember.cards().subList(1, ember.cards().size()));

        assertThatThrownBy(() -> engine.newGame(new GameSetup(tooSmall, content.deck("root-starter"), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs exactly 30 (2.1)");
    }

    @Test
    @DisplayName("5.1.1 — the first player is chosen at random, the same way for the same seed")
    void firstPlayerAtRandom() {
        Set<PlayerId> firstPlayers = new HashSet<>();
        IntStream.rangeClosed(1, 40).forEach(seed -> firstPlayers.add(newGame(seed).state().firstPlayer()));

        assertThat(firstPlayers).containsExactlyInAnyOrder(PlayerId.P1, PlayerId.P2);
        assertThat(newGame(7).state().firstPlayer()).isEqualTo(newGame(7).state().firstPlayer());
        assertThat(newGame(7).events().getFirst()).isInstanceOf(GameEvent.GameStarted.class);
    }

    @Test
    @DisplayName("5.1.2 — each player shuffles their deck and draws 5 cards")
    void openingHands() {
        GameState state = newGame(3).state();

        for (PlayerId id : PlayerId.values()) {
            assertThat(state.player(id).hand()).hasSize(5);
            assertThat(state.player(id).deck()).hasSize(25);
        }
        List<Integer> deckOrder = state.p1().deck().stream().map(card -> card.id().value()).toList();
        assertThat(deckOrder).isNotEqualTo(deckOrder.stream().sorted().toList());
    }

    @Test
    @DisplayName("5.1.3 — a mulligan shuffles the hand back and draws one card fewer")
    void mulligan() {
        Transition start = newGame(5);
        PlayerId first = start.state().firstPlayer();

        Transition after = engine.apply(start.state(), new Action.Mulligan());

        assertThat(after.state().player(first).hand()).hasSize(4);
        assertThat(after.state().player(first).deck()).hasSize(26);
        assertThat(after.events().getFirst()).isEqualTo(new GameEvent.MulliganTaken(first));
        Decision next = engine.decision(after.state()).orElseThrow();
        assertThat(next.kind()).isEqualTo(DecisionKind.MULLIGAN);
        assertThat(next.player()).isEqualTo(first.opponent());
    }

    @Test
    @DisplayName("5.1.3 — the first player decides first, then the second; then turn 1 starts")
    void mulliganOrder() {
        Transition start = newGame(5);
        PlayerId first = start.state().firstPlayer();
        assertThat(engine.decision(start.state()).orElseThrow().player()).isEqualTo(first);

        Transition afterFirst = engine.apply(start.state(), new Action.KeepHand());
        Transition afterSecond = engine.apply(afterFirst.state(), new Action.KeepHand());

        assertThat(afterSecond.state().turn()).isEqualTo(1);
        assertThat(afterSecond.state().active()).isEqualTo(first);
        assertThat(afterSecond.events()).contains(new GameEvent.HandKept(first.opponent()),
                new GameEvent.TurnStarted(first, 1));
    }

    @Test
    @DisplayName("5.2.3 — the first player does not draw on their very first turn; the second player does")
    void noDrawOnTheVeryFirstTurn() {
        Transition turnOne = keepBoth(newGame(9));
        PlayerId first = turnOne.state().firstPlayer();
        assertThat(turnOne.state().player(first).hand()).hasSize(5);

        Transition turnTwo = engine.apply(turnOne.state(), new Action.EndTurn());

        assertThat(turnTwo.state().player(first.opponent()).hand()).hasSize(6);
    }

    @Test
    void cardInstancesFollowTheDecklistsBeforeTheShuffle() {
        GameState state = newGame(11).state();
        List<CardInstance> p1Cards = new ArrayList<>(state.p1().deck());
        state.p1().hand().forEach(card -> p1Cards.add(card.card()));

        assertThat(p1Cards).extracting(card -> card.id().value())
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 30).boxed().toList());
    }

    private Transition newGame(long seed) {
        return engine.newGame(new GameSetup(content.deck("ember-starter"), content.deck("root-starter"), seed));
    }

    private Transition keepBoth(Transition start) {
        Transition afterFirst = engine.apply(start.state(), new Action.KeepHand());
        return engine.apply(afterFirst.state(), new Action.KeepHand());
    }
}
