package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import fr.daliush.shardbound.dataset.recipe.Side;
import fr.daliush.shardbound.dataset.recipe.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A doomed Anchor unit whose protection ended at the start of this turn: healed before the end of the turn, it is no
 * longer doomed and stays; otherwise it is destroyed at the end of the turn (11.3.4, 11.3.5).
 */
public final class AnchorDoomHealRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final List<String> CONTEXT_RULES = List.of("11.3.5");

    @Override
    public String interaction() {
        return "anchor+doom-heal";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        List<Source> heals = Cards.sources(catalog, Effect.Heal.class).stream()
                .filter(source -> Side.hitBy(source.target()).contains(Side.MINE))
                .toList();
        for (UnitCard anchored : Cards.unitsWith(catalog, Keyword.ANCHOR)) {
            scenarios.add(notHealed(catalog, anchored));
            for (Source heal : heals) {
                scenarios.add(healed(catalog, anchored, heal));
            }
        }
        return scenarios;
    }

    /** The events that answer the question: a heal counts only on the doomed unit, not on the other allies. */
    private static Predicate<GameEvent> answers(String unit) {
        return event -> switch (event) {
            case GameEvent.UnitHealed healed -> healed.unit().card().value().equals(unit);
            case GameEvent.DoomLifted ignored -> true;
            case GameEvent.UnitDestroyed ignored -> true;
            default -> false;
        };
    }

    private Scenario notHealed(CardCatalog catalog, UnitCard anchored) {
        String unit = anchored.id().value();
        return new Scenario(String.join("/", interaction(), "not-healed", unit), doomed(catalog, unit).build(),
                List.of(endTurn()), ASKER,
                situation(anchored) + " If I end my turn without healing it, what happens to it?", answers(unit),
                CONTEXT_RULES);
    }

    private Scenario healed(CardCatalog catalog, UnitCard anchored, Source heal) {
        String unit = anchored.id().value();
        return new Scenario(String.join("/", interaction(), "healed", unit, heal.id()),
                heal.prepare(doomed(catalog, unit), ASKER).build(), List.of(heal.play(unit), endTurn()), ASKER,
                situation(anchored) + " If I " + heal.phrase("it") + " and then end my turn, what happens to it?",
                answers(unit), CONTEXT_RULES);
    }

    /** Doomed while protected, then its protection ended at the start of the asker's turn (5.2.1). */
    private static ScenarioBuilder doomed(CardCatalog catalog, String unit) {
        return ScenarioBuilder.of(catalog).unit(ASKER, unit, setUp -> setUp.doomed())
                .deck(ASKER.opponent(), Cards.FILLER);
    }

    private static String situation(UnitCard anchored) {
        return "My " + anchored.name() + " is doomed: it dropped to 0 defense while anchored, and its protection"
                + " ended at the start of this turn.";
    }
}
