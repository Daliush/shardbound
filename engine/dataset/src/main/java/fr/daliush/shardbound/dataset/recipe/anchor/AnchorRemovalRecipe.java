package fr.daliush.shardbound.dataset.recipe.anchor;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
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
 * An effect tries to make an Anchor unit leave the board. While protected, the unit stays and the rest of the card
 * applies (11.3.2, 11.3.3); once its protection is over, it leaves (11.3.6). Every Anchor unit meets every source of
 * the effect, on each side the source can hit, protected or not.
 */
public final class AnchorRemovalRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    private final String interaction;
    private final Class<? extends Effect> effect;
    private final Class<? extends GameEvent> removal;

    private AnchorRemovalRecipe(String interaction, Class<? extends Effect> effect,
            Class<? extends GameEvent> removal) {
        this.interaction = interaction;
        this.effect = effect;
        this.removal = removal;
    }

    /** Destroy (8.2). */
    public static AnchorRemovalRecipe destroy() {
        return new AnchorRemovalRecipe("anchor+destroy", Effect.Destroy.class, GameEvent.UnitDestroyed.class);
    }

    /** Return to hand (8.8). */
    public static AnchorRemovalRecipe returnToHand() {
        return new AnchorRemovalRecipe("anchor+return-to-hand", Effect.ReturnToHand.class,
                GameEvent.ReturnedToHand.class);
    }

    @Override
    public String interaction() {
        return interaction;
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard anchored : Cards.unitsWith(catalog, Keyword.ANCHOR)) {
            for (Source source : Cards.sources(catalog, effect)) {
                for (Side side : Side.hitBy(source.target())) {
                    for (Protection protection : Protection.values()) {
                        scenarios.add(scenario(catalog, anchored, source, side, protection));
                    }
                }
            }
        }
        return scenarios;
    }

    /** The events that answer the question. */
    private boolean answers(GameEvent event) {
        return event instanceof GameEvent.AnchorPrevented || removal.isInstance(event);
    }

    private Scenario scenario(CardCatalog catalog, UnitCard anchored, Source source, Side side,
            Protection protection) {
        String unit = anchored.id().value();
        GameState start = source.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(side.of(ASKER), unit, setUp -> protection.setUp(setUp, side))
                .build();
        String question = protection.situation(anchored.name(), side) + ". If I " + source.phrase("it")
                + ", what happens to it?";
        return new Scenario(String.join("/", interaction, side.label(), protection.label(), unit, source.id()),
                start, List.of(source.play(unit)), ASKER, question, new Answer.Events(this::answers),
                protection.contextRules());
    }
}
