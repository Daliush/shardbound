package fr.daliush.shardbound.dataset.recipe.fracture;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.FractureStep;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.scenario.Choice;
import fr.daliush.shardbound.core.scenario.Choices;
import fr.daliush.shardbound.core.scenario.Pick;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Side;
import java.util.Optional;

/**
 * What the Fracture recipes share: a board where every step has a target (the asker's filler unit, an opponent's
 * sturdy unit, decks to draw from), the play of a step on the right unit, and how a question names a step.
 */
final class FractureSteps {

    static final String ENEMY = "neutral.shard-construct";

    private FractureSteps() {
    }

    /** One unit on each side and a few cards in each deck. */
    static ScenarioBuilder board(ScenarioBuilder board, PlayerId asker) {
        return board.unit(asker, Cards.FILLER).unit(asker.opponent(), ENEMY)
                .deck(asker, Cards.FILLER, Cards.FILLER, Cards.FILLER)
                .deck(asker.opponent(), Cards.FILLER, Cards.FILLER, Cards.FILLER);
    }

    /** Plays the card's next step, aimed at the asker's unit or the opponent's when the step picks one. */
    static Choice play(SpellCard card, FractureStep step) {
        Choices.PlayChoice play = Choices.play(card.id().value());
        return chosenSide(step).map(side -> play.on(Pick.unit(side == Side.MINE ? Cards.FILLER : ENEMY)))
                .<Choice>map(choice -> choice).orElse(play);
    }

    /** The side of the unit the step picks, if it picks one. */
    static Optional<Side> chosenSide(FractureStep step) {
        return step.effects().stream()
                .filter(effect -> effect instanceof Effect.Targeted targeted && targeted.target().isChosen())
                .map(effect -> Side.hitBy(((Effect.Targeted) effect).target()).getFirst())
                .findFirst();
    }

    /** "Moonpull, which has never been played", "Moonpull, ready for its step 2 of 3". */
    static String inHand(SpellCard card, int step) {
        return step == 1 ? card.name() + ", which has never been played"
                : card.name() + ", ready for its step " + step + " of " + card.fracture().size();
    }

    /** " on my Shardling", " on my opponent's Shard Construct", or nothing when the step picks no unit. */
    static String target(CardCatalog catalog, FractureStep step) {
        return chosenSide(step)
                .map(side -> side == Side.MINE ? " on my " + Cards.fillerName(catalog)
                        : " on my opponent's " + enemyName(catalog))
                .orElse("");
    }

    static String enemyName(CardCatalog catalog) {
        return catalog.card(new CardId(ENEMY)).name();
    }
}
