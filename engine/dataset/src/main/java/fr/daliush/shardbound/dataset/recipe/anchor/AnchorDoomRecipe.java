package fr.daliush.shardbound.dataset.recipe.anchor;

import static fr.daliush.shardbound.core.scenario.Choices.endTurn;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.UnitCard;
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
import java.util.ArrayList;
import java.util.List;

/**
 * A spell brings an opponent's Anchor unit to 0 defense. While protected, it is doomed and stays (11.3.4), until the
 * end of the turn its protection ends: then it is destroyed (11.3.5). Once its protection is over, it dies at once.
 * Spells only: the units' attacks would add boards that differ by a number.
 */
public final class AnchorDoomRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    @Override
    public String interaction() {
        return "anchor+doom";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard anchored : Cards.unitsWith(catalog, Keyword.ANCHOR)) {
            for (Source source : Cards.sources(catalog, Effect.Damage.class)) {
                if (source instanceof Source.Spell && Side.hitBy(source.target()).contains(Side.OPPONENTS)) {
                    for (Protection protection : Protection.values()) {
                        scenarios.add(scenario(catalog, anchored, source, protection));
                    }
                }
            }
        }
        return scenarios;
    }

    /** The events that answer the question. */
    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.UnitDoomed || event instanceof GameEvent.AnchorProtectionEnded
                || event instanceof GameEvent.UnitDestroyed;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard anchored, Source source, Protection protection) {
        String anchoredCard = anchored.id().value();
        PlayerId owner = ASKER.opponent();
        int lethalDefense = Math.min(((Effect.Damage) source.effect()).amount(), anchored.defense());
        GameState start = source.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(owner, anchoredCard, setUp -> protection.setUp(setUp, Side.OPPONENTS).defense(lethalDefense))
                .deck(ASKER, Cards.FILLER).deck(owner, Cards.FILLER)
                .build();
        String ifI = protection.situation(anchored.name(), Side.OPPONENTS) + "; it has " + lethalDefense
                + " defense left. If I " + source.phrase("it");
        List<Choice> moves;
        String question;
        if (protection == Protection.PROTECTED) {
            // Doomed, it waits for the end of the turn its protection ends: both players end their turn.
            moves = List.of(source.play(anchoredCard), endTurn(), endTurn());
            question = ifI + " and neither player does anything else, what happens to it?";
        } else {
            moves = List.of(source.play(anchoredCard));
            question = ifI + ", what happens to it?";
        }
        String id = String.join("/", interaction(), protection.label(), anchoredCard, source.id());
        return new Scenario(id, start, moves, ASKER, question, new Answer.Events(AnchorDoomRecipe::answers),
                protection.contextRules());
    }
}
