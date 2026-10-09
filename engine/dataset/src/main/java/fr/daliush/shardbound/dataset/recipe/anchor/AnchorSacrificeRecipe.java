package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.core.scenario.Pick.unit;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import fr.daliush.shardbound.dataset.recipe.Side;
import java.util.ArrayList;
import java.util.List;

/**
 * An Anchor unit is sacrificed to pay a card's sacrifice cost. While protected, it stays, the sacrifice still counts
 * as paid and the card resolves (11.3.3); once its protection is over, it dies (8.3). A card that needs a target aims
 * at an enemy unit.
 */
public final class AnchorSacrificeRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "anchor+sacrifice";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard anchored : Cards.unitsWith(catalog, Keyword.ANCHOR)) {
            for (CardDefinition card : Cards.withSacrificeCost(catalog)) {
                for (Protection protection : Protection.values()) {
                    scenarios.add(scenario(catalog, anchored, card, protection));
                }
            }
        }
        return scenarios;
    }

    /** The events that answer the question. */
    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.AnchorPrevented || event instanceof GameEvent.UnitSacrificed
                || event instanceof GameEvent.UnitDestroyed || event instanceof GameEvent.UnitDamaged
                || event instanceof GameEvent.UnitArrived;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard anchored, CardDefinition card, Protection protection) {
        String unit = anchored.id().value();
        GameState start = ScenarioBuilder.of(catalog)
                .shards(ASKER, Cards.cost(card)).hand(ASKER, card.id().value())
                .unit(ASKER, unit, setUp -> protection.setUp(setUp, Side.MINE))
                .unit(ASKER.opponent(), Cards.FILLER)
                .build();
        Choices.PlayChoice play = Choices.play(card.id().value()).sacrificing(unit(unit));
        boolean aimed = needsATarget(card);
        String question = protection.situation(anchored.name(), Side.MINE) + ". If I sacrifice it to play "
                + card.name() + (aimed ? " on my opponent's " + catalog.card(new CardId(Cards.FILLER)).name() : "")
                + ", what happens?";
        return new Scenario(String.join("/", interaction(), protection.label(), unit, card.id().value()), start,
                List.of(aimed ? play.on(unit(Cards.FILLER)) : play), ASKER, question, AnchorSacrificeRecipe::answers,
                protection.contextRules());
    }

    /** A spell whose effect aims at a chosen target; a unit with a sacrifice cost has none yet. */
    private static boolean needsATarget(CardDefinition card) {
        return card instanceof SpellCard spell && spell.effects().stream()
                .anyMatch(effect -> effect instanceof Effect.Targeted targeted && targeted.target().isChosen());
    }
}
