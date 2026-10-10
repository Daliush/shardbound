package fr.daliush.shardbound.dataset.recipe.general;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Duration;
import fr.daliush.shardbound.core.content.Effect;
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
import fr.daliush.shardbound.dataset.recipe.StateFacts;
import java.util.ArrayList;
import java.util.List;

/**
 * A defense change "until end of turn" ends with the turn (5.4.2). A bonus ending lowers the max, and the current
 * defense only drops to the new max (8.5): a damaged unit keeps its defense. A malus ending gives back what it took
 * (8.17), after damage taken in between; a malus that brings the max to 0 destroys the unit (6.10).
 */
public final class ModifyExpiryRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final String STURDY = "neutral.shard-construct";
    private static final int DAMAGED = 1;

    @Override
    public String interaction() {
        return "modify+expiry";
    }

    @Override
    public Category category() {
        return Category.MULTI_STEP;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        Source hit = Cards.spellSources(catalog, Effect.Damage.class, Side.OPPONENTS).stream()
                .filter(source -> source.target().isChosen()).findFirst().orElseThrow();
        for (Source modify : Cards.sources(catalog, Effect.Modify.class)) {
            Effect.Modify effect = (Effect.Modify) modify.effect();
            if (modify instanceof Source.Spell && effect.duration() == Duration.END_OF_TURN && effect.defense() != 0
                    && modify.target().isChosen()) {
                if (effect.defense() > 0) {
                    scenarios.add(bonus(catalog, modify, false));
                    scenarios.add(bonus(catalog, modify, true));
                } else {
                    scenarios.add(malusThenHit(catalog, modify, hit));
                    scenarios.add(lethalMalus(catalog, modify));
                }
            }
        }
        return scenarios;
    }

    /** The asker boosts their unit, full or damaged, then ends their turn. */
    private Scenario bonus(CardCatalog catalog, Source bonus, boolean damaged) {
        String unit = Cards.FILLER;
        String name = Cards.fillerName(catalog);
        int max = catalog.unit(new CardId(unit)).defense();
        int defense = damaged ? DAMAGED : max;
        GameState start = bonus.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(ASKER, unit, setUp -> setUp.defense(defense))
                .deck(ASKER.opponent(), Cards.FILLER)
                .build();
        String question = "My " + name + " has " + defense + " defense, out of " + max + ". If I "
                + bonus.phrase(name) + " and then end my turn, what happens to it?";
        String id = String.join("/", interaction(), damaged ? "bonus-damaged" : "bonus-full", bonus.id());
        return new Scenario(id, start, List.of(bonus.play(unit), endTurn()), ASKER, question,
                new Answer.Events(ModifyExpiryRecipe::answers, StateFacts.defenseOf(unit, name)),
                List.of("8.5", "5.4.2"));
    }

    /** The asker weakens a sturdy enemy unit, hits it, then ends their turn: the malus gives back what it took. */
    private Scenario malusThenHit(CardCatalog catalog, Source malus, Source hit) {
        String name = catalog.card(new CardId(STURDY)).name();
        GameState start = hit.place(malus.place(ScenarioBuilder.of(catalog), ASKER), ASKER)
                .shards(ASKER, malus.cost() + hit.cost())
                .unit(ASKER.opponent(), STURDY)
                .deck(ASKER.opponent(), Cards.FILLER)
                .build();
        List<Choice> moves = List.of(malus.play(STURDY), hit.play(STURDY), endTurn());
        String question = "My opponent's " + name + " has full defense. If I " + malus.phrase(name) + ", then "
                + hit.phrase(name) + ", and end my turn, what happens to it?";
        return new Scenario(String.join("/", interaction(), "malus-then-hit", malus.id(), hit.id()), start, moves,
                ASKER, question, new Answer.Events(ModifyExpiryRecipe::answers, StateFacts.defenseOf(STURDY, name)),
                List.of("8.17", "5.4.2"));
    }

    /** The malus takes all of a small unit's max defense. */
    private Scenario lethalMalus(CardCatalog catalog, Source malus) {
        String name = Cards.fillerName(catalog);
        GameState start = malus.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(ASKER.opponent(), Cards.FILLER)
                .build();
        String question = "My opponent's " + name + " has full defense. If I " + malus.phrase(name)
                + ", what happens to it?";
        return new Scenario(String.join("/", interaction(), "lethal-malus", malus.id()), start,
                List.of(malus.play(Cards.FILLER)), ASKER, question, new Answer.Events(ModifyExpiryRecipe::answers),
                List.of("6.10"));
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.Modified || event instanceof GameEvent.ModifierExpired
                || event instanceof GameEvent.UnitDamaged || event instanceof GameEvent.UnitDestroyed;
    }
}
