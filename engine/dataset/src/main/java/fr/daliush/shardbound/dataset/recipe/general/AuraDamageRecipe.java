package fr.daliush.shardbound.dataset.recipe.general;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.RelicCard;
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
import java.util.Optional;

/**
 * A damage spell hits an opponent's unit that a stat aura boosts (8.14): the defense the aura gives can keep it alive.
 * Each spell is also asked without the aura, where the same hit kills the unit. No card can remove an aura yet (the
 * relics that carry them cannot be destroyed or returned), so its end is not part of the interaction.
 */
public final class AuraDamageRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "aura+damage";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (Source source : Cards.spellSources(catalog, Effect.Damage.class, Side.OPPONENTS)) {
            scenarios.add(scenario(catalog, source, Optional.empty()));
            for (CardDefinition aura : Cards.withStatAura(catalog)) {
                scenarios.add(scenario(catalog, source, Optional.of(aura)));
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, Source source, Optional<CardDefinition> aura) {
        ScenarioBuilder board = source.prepare(ScenarioBuilder.of(catalog), ASKER);
        aura.ifPresent(card -> place(board, card));
        GameState start = board.unit(ASKER.opponent(), Cards.FILLER).build();
        String filler = Cards.fillerName(catalog);
        String question = "My opponent has " + aura.map(card -> card.name() + " and ").orElse("") + "a " + filler
                + " on their board. If I " + source.phrase(filler) + ", what happens?";
        String variant = aura.map(card -> "with-" + card.id().value()).orElse("no-aura");
        return new Scenario(String.join("/", interaction(), variant, source.id()), start,
                List.of(source.play(Cards.FILLER)), ASKER, question, new Answer.Events(AuraDamageRecipe::answers),
                aura.isPresent() ? List.of("8.14") : List.of());
    }

    private static void place(ScenarioBuilder board, CardDefinition aura) {
        if (aura instanceof RelicCard) {
            board.relic(ASKER.opponent(), aura.id().value());
        } else {
            board.unit(ASKER.opponent(), aura.id().value());
        }
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.AuraApplied || event instanceof GameEvent.UnitDamaged
                || event instanceof GameEvent.UnitDestroyed;
    }
}
