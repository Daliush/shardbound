package fr.daliush.shardbound.dataset.recipe.general;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.Pick;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import fr.daliush.shardbound.dataset.recipe.StateFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A spell removes the opponent's relic that carries a stat aura. The aura stops applying, and its defense bonus ends
 * like an expiring bonus (8.14, 8.5): the max drops, and the current defense only drops to the new max. The boosted
 * unit is at full defense, or damaged below its own max.
 */
public final class AuraEndRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final int DAMAGED = 1;

    @Override
    public String interaction() {
        return "aura+end";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<SpellCard> removers = Stream.of(Effect.Destroy.class, Effect.ReturnToHand.class)
                .flatMap(effect -> Cards.spellsWith(catalog, effect).stream())
                .filter(spell -> spell.effects().stream().anyMatch(effect -> effect.targets(TargetSpec.ENEMY_RELIC)
                        || effect.targets(TargetSpec.ANY_RELIC)))
                .toList();
        List<Scenario> scenarios = new ArrayList<>();
        for (CardDefinition aura : Cards.withStatAura(catalog)) {
            if (aura instanceof RelicCard relic) {
                for (SpellCard remover : removers) {
                    scenarios.add(scenario(catalog, relic, remover, false));
                    scenarios.add(scenario(catalog, relic, remover, true));
                }
            }
        }
        return scenarios;
    }

    private Scenario scenario(CardCatalog catalog, RelicCard aura, SpellCard remover, boolean damaged) {
        String boosted = Cards.FILLER;
        String boostedName = Cards.fillerName(catalog);
        int baseMax = catalog.unit(new CardId(boosted)).defense();
        int bonus = auraDefense(aura);
        int base = damaged ? DAMAGED : baseMax;
        GameState start = ScenarioBuilder.of(catalog).shards(ASKER, Cards.cost(remover))
                .hand(ASKER, remover.id().value())
                .relic(ASKER.opponent(), aura.id().value())
                .unit(ASKER.opponent(), boosted, setUp -> setUp.defense(base))
                .build();
        String question = "My opponent has " + aura.name() + " and a " + boostedName + " that it boosts to "
                + (base + bonus) + " defense, out of " + (baseMax + bonus) + ". If I cast " + remover.name() + " on "
                + aura.name() + ", what happens?";
        String id = String.join("/", interaction(), damaged ? "damaged" : "full", aura.id().value(),
                remover.id().value());
        return new Scenario(id, start,
                List.of(Choices.play(remover.id().value()).on(Pick.relic(aura.id().value()))), ASKER, question,
                new Answer.Events(AuraEndRecipe::answers, StateFacts.defenseOf(boosted, boostedName)),
                List.of("8.14", "8.5"));
    }

    private static int auraDefense(RelicCard aura) {
        return aura.abilities().stream().flatMap(ability -> ability.effects().stream())
                .filter(Effect.StatAura.class::isInstance)
                .mapToInt(effect -> ((Effect.StatAura) effect).defense())
                .findFirst().orElse(0);
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.RelicDestroyed || event instanceof GameEvent.ReturnedToHand
                || event instanceof GameEvent.AuraRemoved || event instanceof GameEvent.UnitDestroyed;
    }
}
