package fr.daliush.shardbound.dataset.recipe.link;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
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
import java.util.stream.Stream;

/**
 * A spell destroys, freezes or returns to hand a linked unit: only damage is shared, so its partner is untouched
 * (11.5.3); the link breaks when either unit leaves the board (11.5.4). Every such spell that picks an opponent's unit,
 * with the partner on either side.
 */
public final class LinkNoShareRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "link+no-share";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Source> sources = Stream.of(Effect.Destroy.class, Effect.Freeze.class, Effect.ReturnToHand.class)
                .flatMap(effect -> Cards.spellSources(catalog, effect, Side.OPPONENTS).stream())
                .filter(source -> source.target().isChosen())
                .toList();
        List<Scenario> scenarios = new ArrayList<>();
        for (Source source : sources) {
            for (Side partnerSide : Side.values()) {
                scenarios.add(scenario(catalog, source, partnerSide));
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, Source source, Side partnerSide) {
        // A spell may draw too (Rime Snare): a deck keeps the draw from costing fatigue.
        GameState start = LinkedPair.place(source.prepare(ScenarioBuilder.of(catalog), ASKER), ASKER, partnerSide)
                .deck(ASKER, Cards.FILLER)
                .build();
        String question = Cards.turnFor(source.effect()) + LinkedPair.situation(catalog, partnerSide) + " If I "
                + source.phrase(LinkedPair.targetName(catalog)) + ", what happens?";
        List<String> contextRules = Stream.concat(Stream.of("11.5.3"), LinkedPair.sideRules(partnerSide).stream())
                .toList();
        return new Scenario(String.join("/", interaction(), "partner-" + partnerSide.label(), source.id()), start,
                List.of(source.play(LinkedPair.TARGET)), ASKER, question,
                new Answer.Events(LinkNoShareRecipe::answers), contextRules);
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.UnitDestroyed || event instanceof GameEvent.Frozen
                || event instanceof GameEvent.ReturnedToHand || event instanceof GameEvent.LinkBroken;
    }
}
