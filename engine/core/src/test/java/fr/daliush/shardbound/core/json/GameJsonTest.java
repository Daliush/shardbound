package fr.daliush.shardbound.core.json;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.bot.RandomBot;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.testing.GameDriver;
import fr.daliush.shardbound.core.testing.GameDriver.PlayedGame;
import fr.daliush.shardbound.core.testing.TestContent;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GameJsonTest {

    private final Content content = TestContent.content();
    private final GameEngine engine = new GameEngine(content.catalog());
    private final GameJson json = new GameJson();

    @Test
    void everyStateAndEventOfAGameSurvivesAJsonRoundTrip() {
        for (int seed = 1; seed <= 10; seed++) {
            PlayedGame game = GameDriver.play(engine, setup(seed), new RandomBot(seed), new RandomBot(seed + 50),
                    (state, decision) -> assertThat(json.readState(json.write(state))).isEqualTo(state));
            assertThat(json.readEvents(json.writeEvents(game.events()))).isEqualTo(game.events());
        }
    }

    @Test
    void aRestoredStatePlaysOnExactlyLikeTheOriginal() {
        GameState midGame = stateAfter(40);
        GameState restored = json.readState(json.write(midGame));

        assertThat(playToTheEnd(restored)).isEqualTo(playToTheEnd(midGame));
    }

    private GameState stateAfter(int decisions) {
        GameState state = engine.newGame(setup(3)).state();
        RandomBot bot = new RandomBot(9);
        for (int i = 0; i < decisions; i++) {
            Decision decision = engine.decision(state).orElseThrow();
            state = engine.apply(state, bot.choose(null, decision)).state();
        }
        return state;
    }

    private GameState playToTheEnd(GameState start) {
        GameState state = start;
        RandomBot bot = new RandomBot(77);
        for (Optional<Decision> decision = engine.decision(state); decision.isPresent();
             decision = engine.decision(state)) {
            state = engine.apply(state, bot.choose(null, decision.get())).state();
        }
        return state;
    }

    private GameSetup setup(int seed) {
        return new GameSetup(content.deck("ember-starter"), content.deck("root-starter"), seed);
    }
}
