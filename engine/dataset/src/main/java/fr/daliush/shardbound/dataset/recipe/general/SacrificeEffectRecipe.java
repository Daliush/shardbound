package fr.daliush.shardbound.dataset.recipe.general;

import static fr.daliush.shardbound.core.scenario.Choices.sacrifice;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.Pick;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Moves;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import java.util.ArrayList;
import java.util.List;

/**
 * A spell with a Sacrifice effect: its controller sacrifices their own units, then the rest applies (8.3). It cannot
 * be played without the units to sacrifice (8.16); with more units than it asks for, its controller chooses; an
 * anchored unit still protected counts but stays (11.3.3).
 */
public final class SacrificeEffectRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;
    private static final String OTHER = "neutral.shard-construct";

    @Override
    public String interaction() {
        return "sacrifice-effect";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (SpellCard spell : Cards.spellsWith(catalog, Effect.Sacrifice.class)) {
            scenarios.add(noUnit(catalog, spell));
            scenarios.add(oneUnit(catalog, spell));
            scenarios.add(twoUnits(catalog, spell));
            for (UnitCard anchored : Cards.unitsWith(catalog, Keyword.ANCHOR)) {
                scenarios.add(protectedAnchor(catalog, spell, anchored));
            }
        }
        return scenarios;
    }

    private Scenario noUnit(CardCatalog catalog, SpellCard spell) {
        String cast = "cast " + spell.name() + ".";
        return new Scenario(id("no-unit", spell), board(catalog, spell).build(), List.of(), ASKER,
                "I have no unit on my board. Can I cast " + spell.name() + "?",
                new Answer.Legality(Moves.playing(spell.id().value()), "You can " + cast, "You cannot " + cast),
                List.of("8.16"));
    }

    private Scenario oneUnit(CardCatalog catalog, SpellCard spell) {
        String question = "My only unit is a " + Cards.fillerName(catalog) + ". If I cast " + spell.name()
                + ", what happens?";
        return new Scenario(id("one-unit", spell), board(catalog, spell).unit(ASKER, Cards.FILLER).build(),
                List.of(Choices.play(spell.id().value())), ASKER, question,
                new Answer.Events(SacrificeEffectRecipe::answers), List.of("8.3"));
    }

    /** More units than it asks for: the asker chooses the filler (8.16). */
    private Scenario twoUnits(CardCatalog catalog, SpellCard spell) {
        String other = catalog.card(new CardId(OTHER)).name();
        String question = "I have a " + Cards.fillerName(catalog) + " and a " + other + ". If I cast " + spell.name()
                + " and sacrifice my " + Cards.fillerName(catalog) + ", what happens?";
        return new Scenario(id("two-units", spell),
                board(catalog, spell).unit(ASKER, Cards.FILLER).unit(ASKER, OTHER).build(),
                List.of(Choices.play(spell.id().value()), sacrifice(Pick.unit(Cards.FILLER))), ASKER, question,
                new Answer.Events(SacrificeEffectRecipe::answers), List.of("8.16"));
    }

    private Scenario protectedAnchor(CardCatalog catalog, SpellCard spell, UnitCard anchored) {
        String anchoredCard = anchored.id().value();
        String question = "I played " + anchored.name() + " this turn, and it is my only unit. If I cast "
                + spell.name() + ", what happens?";
        return new Scenario(id("protected-anchor", spell) + "/" + anchoredCard,
                board(catalog, spell).unit(ASKER, anchoredCard, setUp -> setUp.arrivedThisTurn().anchorProtected())
                        .build(),
                List.of(Choices.play(spell.id().value())), ASKER, question,
                new Answer.Events(SacrificeEffectRecipe::answers), List.of("8.16", "11.3.1", "11.3.3"));
    }

    private static ScenarioBuilder board(CardCatalog catalog, SpellCard spell) {
        return ScenarioBuilder.of(catalog).shards(ASKER, Cards.cost(spell)).hand(ASKER, spell.id().value());
    }

    private String id(String variant, SpellCard spell) {
        return String.join("/", interaction(), variant, spell.id().value());
    }

    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.UnitSacrificed || event instanceof GameEvent.AnchorPrevented
                || event instanceof GameEvent.PlayerDamaged || event instanceof GameEvent.SacrificeFailed;
    }
}
