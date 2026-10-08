package fr.daliush.shardbound.core.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.Player;
import fr.daliush.shardbound.core.bot.random.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.testing.GameDriver;
import fr.daliush.shardbound.core.testing.HiddenCards;
import fr.daliush.shardbound.core.testing.TestCards;
import fr.daliush.shardbound.core.testing.TestContent;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** A player's view never holds the opponent's hand or a deck order (3.2, 3.3, 3.7). */
class HiddenInformationTest {

    private final Content content = TestContent.content();
    private final GameEngine engine = new GameEngine(content.catalog());

    @Test
    void viewsNeverShowWhatThePlayerMayNotSee() {
        for (int seed = 1; seed <= 20; seed++) {
            GameSetup setup = new GameSetup(content.deck("ember-starter"), content.deck("root-starter"), seed);
            GameDriver.play(engine, setup, checking(new RandomBot(seed)), checking(new RandomBot(-seed)));
        }
    }

    @Test
    void choicesOfCardsOnlyShowTheDecidersOwnCards() {
        GameEngine withTestCards = new GameEngine(TestCards.CATALOG);
        for (int seed = 1; seed <= 20; seed++) {
            GameSetup setup = new GameSetup(TestCards.EFFECTS_DECK, content.deck("ember-starter"), seed);
            GameDriver.play(withTestCards, setup, checking(new RandomBot(seed)), checking(new RandomBot(-seed)));
        }
    }

    /** The strongest form: a view is a function of what its player may see, the resolution in progress included. */
    @Test
    void aViewIsTheSameWhateverTheCardsItsPlayerMayNotSee() {
        List<String> decks = List.of("ember-starter", "root-starter", "tide-starter");
        AtomicInteger midResolution = new AtomicInteger();
        for (int seed = 1; seed <= 30; seed++) {
            GameSetup setup = new GameSetup(content.deck(decks.get(seed % 3)), content.deck(decks.get((seed + 1) % 3)),
                    seed);
            GameDriver.play(engine, setup, new RandomBot(seed), new RandomBot(-seed), (state, decision) -> {
                for (PlayerId viewer : PlayerId.values()) {
                    GameState other = HiddenCards.rearranged(state, viewer);
                    assertThat(engine.view(other, viewer, List.of())).isEqualTo(engine.view(state, viewer, List.of()));
                }
                if (!state.resolution().isIdle()) {
                    midResolution.incrementAndGet();
                }
            });
        }
        assertThat(midResolution).as("views of games paused in the middle of a resolution").hasPositiveValue();
    }

    /** Wraps a bot so that every view it receives is checked first. */
    private static Player checking(Player bot) {
        return (view, decision) -> {
            check(view, decision);
            return bot.choose(view, decision);
        };
    }

    private static void check(PlayerView view, Decision decision) {
        PlayerId opponent = view.viewer().opponent();
        assertThat(view.opponent().handCount()).isNotNegative();
        assertThat(view.decision()).contains(decision);
        for (GameEvent event : view.history()) {
            if (event instanceof GameEvent.CardDrawn drawn && drawn.player() == opponent) {
                assertThat(drawn.card()).as("an opponent's draw is redacted").isEmpty();
            }
        }
        for (Action action : decision.actions()) {
            if (action instanceof Action.PlayCard play) {
                assertThat(view.self().hand()).anyMatch(card -> card.id().equals(play.card()));
            }
            if (action instanceof Action.ChooseCards choice) {
                assertThat(choice.cards()).as("a discard or a sacrifice picks the decider's own cards")
                        .allMatch(id -> view.self().hand().stream().anyMatch(card -> card.id().equals(id))
                                || view.self().units().stream().anyMatch(unit -> unit.id().equals(id)));
            }
        }
        for (Step step : view.resolution().steps()) {
            if (step instanceof Step.FinishSpell finish) {
                assertThat(view.history()).as("a spell the resolution holds was played in public")
                        .anyMatch(event -> event instanceof GameEvent.CardPlayed played
                                && played.card().equals(finish.spell().card()));
            }
        }
        assertThat(view.self().hand()).allMatch(card -> card.card().owner() == view.viewer());
        assertThat(view.opponent().graveyard()).extracting(CardInstance::owner).allMatch(owner -> owner == opponent);
    }
}
