package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choice;
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
 * A spell brings an opponent's Anchor unit to 0 defense. While protected, it is doomed and stays (11.3.4), until the
 * end of the turn its protection ends: then it is destroyed (11.3.5). Once its protection is over, it dies at once.
 * Spells only: the units' attacks would add boards that differ by a number.
 */
public final class AnchorDoomRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "anchor+doom";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard anchored : Cards.unitsWith(catalog, Keyword.ANCHOR)) {
            for (Source source : Cards.sources(catalog, Effect.Damage.class)) {
                if (source instanceof Source.Spell && Side.hitBy(source.target()).contains(Side.OPPONENTS)) {
                    for (Protection protection : Protection.values()) {
                        scenarios.add(scenario(catalog, anchored, source, protection));
                    }
                }
            }
        }
        return scenarios;
    }

    /** The events that answer the question. */
    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.UnitDoomed || event instanceof GameEvent.AnchorProtectionEnded
                || event instanceof GameEvent.UnitDestroyed;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard anchored, Source source, Protection protection) {
        String unit = anchored.id().value();
        int lethal = Math.min(((Effect.Damage) source.effect()).amount(), anchored.defense());
        GameState start = source.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(ASKER.opponent(), unit, setUp -> protection.setUp(setUp, Side.OPPONENTS).defense(lethal))
                .deck(ASKER, Cards.FILLER).deck(ASKER.opponent(), Cards.FILLER)
                .build();
        String situation = protection.situation(anchored.name(), Side.OPPONENTS) + "; it has " + lethal
                + " defense left. If I " + source.phrase("it");
        // While doomed it waits for the end of its controller's next turn: both players end their turn.
        List<Choice> choices = protection == Protection.PROTECTED
                ? List.of(source.play(unit), endTurn(), endTurn())
                : List.of(source.play(unit));
        String question = protection == Protection.PROTECTED
                ? situation + " and neither player does anything else, what happens to it?"
                : situation + ", what happens to it?";
        return new Scenario(String.join("/", interaction(), protection.label(), unit, source.id()), start, choices,
                ASKER, question, new Answer.Events(AnchorDoomRecipe::answers), protection.contextRules());
    }
}
