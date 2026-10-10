package fr.daliush.shardbound.dataset.recipe.general;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Trigger;
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
import fr.daliush.shardbound.dataset.recipe.Source;
import java.util.ArrayList;
import java.util.List;

/**
 * A unit with an "Attack" ability attacks. The ability resolves once the attack is settled and before the defender
 * may intercept (9.9). When it kills the target first, the attack still takes place but does nothing to it (7.9); when
 * it kills the only unit that could intercept, nothing is left to intercept with (7.4, 7.5).
 */
public final class AttackAbilityRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final String STURDY = "neutral.shard-construct";
    private static final int FRAGILE = 1;

    @Override
    public String interaction() {
        return "attack-ability";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<UnitCard> attackers = Cards.unitsWith(catalog, Trigger.ATTACK);
        List<Scenario> scenarios = new ArrayList<>();
        for (Source attack : Cards.sources(catalog, Effect.Damage.class)) {
            if (attack instanceof Source.Attack source && attackers.contains(source.unit())) {
                scenarios.add(sturdyTarget(catalog, attack));
                scenarios.add(fragileTarget(catalog, attack));
                scenarios.add(fragileInterceptor(catalog, attack));
            }
        }
        return scenarios;
    }

    private Scenario sturdyTarget(CardCatalog catalog, Source attack) {
        GameState start = attack.prepare(ScenarioBuilder.of(catalog), ASKER).unit(ASKER.opponent(), STURDY).build();
        String question = "My opponent's only unit is a " + name(catalog, STURDY) + " at full defense. If I "
                + attack.phrase(name(catalog, STURDY)) + ", what happens?";
        return scenario("sturdy-target", attack, STURDY, start, question, List.of("9.9"));
    }

    private Scenario fragileTarget(CardCatalog catalog, Source attack) {
        String target = Cards.FILLER;
        GameState start = attack.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(ASKER.opponent(), target, setUp -> setUp.defense(FRAGILE)).build();
        String question = "My opponent's only unit is a " + Cards.fillerName(catalog) + " with " + FRAGILE
                + " defense left. If I " + attack.phrase(Cards.fillerName(catalog)) + ", what happens?";
        return scenario("fragile-target", attack, target, start, question, List.of("9.9", "7.9"));
    }

    private Scenario fragileInterceptor(CardCatalog catalog, Source attack) {
        GameState start = attack.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(ASKER.opponent(), STURDY)
                .unit(ASKER.opponent(), Cards.FILLER, setUp -> setUp.defense(FRAGILE)).build();
        String question = "My opponent has a " + name(catalog, STURDY) + " at full defense and a "
                + Cards.fillerName(catalog) + " with " + FRAGILE + " defense left. If I "
                + attack.phrase(name(catalog, STURDY)) + ", can the " + Cards.fillerName(catalog)
                + " intercept, and what happens?";
        return scenario("fragile-interceptor", attack, STURDY, start, question, List.of("9.9", "7.4", "7.5"));
    }

    private Scenario scenario(String variant, Source attack, String target, GameState start, String question,
            List<String> contextRules) {
        return new Scenario(String.join("/", interaction(), variant, attack.id()), start,
                List.of(attack.play(target)), ASKER, question, new Answer.Events(AttackAbilityRecipe::answers),
                contextRules);
    }

    private static String name(CardCatalog catalog, String card) {
        return catalog.card(new CardId(card)).name();
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.AbilityTriggered || event instanceof GameEvent.UnitDamaged
                || event instanceof GameEvent.UnitDestroyed;
    }
}
