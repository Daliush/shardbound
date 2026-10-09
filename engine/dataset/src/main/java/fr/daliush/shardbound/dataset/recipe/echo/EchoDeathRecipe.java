package fr.daliush.shardbound.dataset.recipe.echo;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import fr.daliush.shardbound.dataset.recipe.Side;
import fr.daliush.shardbound.dataset.recipe.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * A spell kills an opponent's Echo unit. Its echo replays at X% (11.1.1, 11.1.2), aimed by its owner with the
 * targeting rules of an attack (11.1.3, 7.3): at the asker's only unit, or at the asker when they have none.
 */
public final class EchoDeathRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final List<String> CONTEXT_RULES = List.of("11.1.3", "7.3");

    /** What the asker has on their board, so what the echo can hit. */
    private enum Board {
        ONE_UNIT, NO_UNIT;

        String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    @Override
    public String interaction() {
        return "echo+death";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Source> killers = Stream.concat(
                Cards.spellSources(catalog, Effect.Destroy.class, Side.OPPONENTS).stream(),
                Cards.spellSources(catalog, Effect.Damage.class, Side.OPPONENTS).stream()).toList();
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard echo : Cards.unitsWithEcho(catalog)) {
            for (Source killer : killers) {
                for (Board board : Board.values()) {
                    scenarios.add(scenario(catalog, echo, killer, board));
                }
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard echo, Source killer, Board board) {
        String echoCard = echo.id().value();
        OptionalInt lethal = Echoes.lethalDefense(killer, echo);
        ScenarioBuilder start = killer.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(ASKER.opponent(), echoCard, setUp -> Echoes.withDefense(setUp, lethal));
        if (board == Board.ONE_UNIT) {
            start.unit(ASKER, Cards.FILLER);
        }
        String state = lethal.isPresent() ? " has " + lethal.getAsInt() + " defense left" : " is on the board";
        String mine = board == Board.ONE_UNIT ? ", and my only unit is a " + Cards.fillerName(catalog) + "."
                : ", and I have no unit on my board.";
        String question = "My opponent's " + echo.name() + state + mine + " If I " + killer.phrase("it")
                + ", what happens?";
        return new Scenario(String.join("/", interaction(), board.label(), echoCard, killer.id()), start.build(),
                List.of(killer.play(echoCard)), ASKER, question, new Answer.Events(Echoes::isDeathOrEcho),
                CONTEXT_RULES);
    }
}
