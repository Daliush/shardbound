package fr.daliush.shardbound.dataset.recipe.echo;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
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

/**
 * The asker boosts the damage of their units, then a spell destroys every unit, the asker's Echo unit included. The
 * echo replays the attack as printed: the boost is lost with the unit (11.1.9, 8.18). The opponent has no unit, so
 * the echo hits them.
 */
public final class EchoModifyRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "echo+modify";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Source> boosts = Cards.sources(catalog, Effect.Modify.class).stream()
                .filter(source -> ((Effect.Modify) source.effect()).attackDamage() > 0
                        && Side.hitBy(source.target()).contains(Side.MINE))
                .toList();
        List<Source> sweepers = Cards.spellSources(catalog, Effect.Destroy.class, Side.MINE).stream()
                .filter(source -> source.target().isGroup())
                .toList();
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard echo : Cards.unitsWithEcho(catalog)) {
            for (Source boost : boosts) {
                if (!isAttackOf(boost, echo)) {
                    for (Source sweeper : sweepers) {
                        scenarios.add(scenario(catalog, echo, boost, sweeper));
                    }
                }
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard echo, Source boost, Source sweeper) {
        String echoCard = echo.id().value();
        GameState start = sweeper.place(boost.place(ScenarioBuilder.of(catalog), ASKER), ASKER)
                .unit(ASKER, echoCard)
                .shards(ASKER, boost.cost() + sweeper.cost())
                .build();
        String mine = boost instanceof Source.Attack attack ? echo.name() + " and " + attack.unit().name()
                : echo.name();
        String question = "I have " + mine + " on my board, and my opponent has no unit. If I "
                + boost.phrase(echo.name()) + " and then " + sweeper.phrase(echo.name()) + ", what happens?";
        return new Scenario(String.join("/", interaction(), echoCard, boost.id(), sweeper.id()), start,
                List.of(boost.play(echoCard), sweeper.play(echoCard)), ASKER, question,
                new Answer.Events(event -> event instanceof GameEvent.Modified || Echoes.isDeathOrEcho(event)),
                List.of("11.1.9", "8.18"));
    }

    /** The boost is an attack of a unit with the same card: two copies would read as one in the answer. */
    private static boolean isAttackOf(Source boost, UnitCard echo) {
        return boost instanceof Source.Attack attack && attack.unit().equals(echo);
    }
}
