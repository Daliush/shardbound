package fr.daliush.shardbound.core.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.Player;
import fr.daliush.shardbound.core.bot.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.testing.GameDriver;
import fr.daliush.shardbound.core.testing.TestContent;
import fr.daliush.shardbound.core.view.PlayerView;
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
        }
        assertThat(view.self().hand()).allMatch(card -> card.card().owner() == view.viewer());
        assertThat(view.opponent().graveyard()).extracting(CardInstance::owner).allMatch(owner -> owner == opponent);
    }
}
