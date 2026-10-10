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

/**
 * A damage spell hits a linked unit: the unit hit takes half, rounded up, and its partner the rest; the share is not
 * shared again (11.5.2). Every damage spell that hits the opponent, with the partner on either side.
 */
public final class LinkDamageRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "link+damage";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (Source source : Cards.spellSources(catalog, Effect.Damage.class, Side.OPPONENTS)) {
            for (Side partnerSide : Side.values()) {
                scenarios.add(scenario(catalog, source, partnerSide));
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, Source source, Side partnerSide) {
        GameState start = LinkedPair.place(source.prepare(ScenarioBuilder.of(catalog), ASKER), ASKER, partnerSide)
                .build();
        String question = LinkedPair.situation(catalog, partnerSide) + " If I "
                + source.phrase(LinkedPair.targetName(catalog)) + ", what happens?";
        return new Scenario(String.join("/", interaction(), "partner-" + partnerSide.label(), source.id()), start,
                List.of(source.play(LinkedPair.TARGET)), ASKER, question,
                new Answer.Events(LinkDamageRecipe::answers), LinkedPair.sideRules(partnerSide));
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.UnitDamaged || event instanceof GameEvent.DamageShared
                || event instanceof GameEvent.UnitDestroyed || event instanceof GameEvent.LinkBroken;
    }
}
