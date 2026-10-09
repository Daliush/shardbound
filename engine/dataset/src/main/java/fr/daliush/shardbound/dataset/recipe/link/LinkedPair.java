package fr.daliush.shardbound.dataset.recipe.link;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Side;
import java.util.List;

/**
 * The two units the Link recipes link: an opponent's sturdy unit, the one the spell aims at, and a filler unit on
 * either side, since a link can join units of both sides (8.11).
 */
final class LinkedPair {

    static final String TARGET = "neutral.shard-construct";

    private LinkedPair() {
    }

    /** Places both units and links them; the partner sits on {@code partnerSide}, seen from the asker. */
    static ScenarioBuilder place(ScenarioBuilder board, PlayerId asker, Side partnerSide) {
        return board.unit(asker.opponent(), TARGET).unit(partnerSide.of(asker), Cards.FILLER)
                .link(TARGET, Cards.FILLER);
    }

    /** "My opponent's Shard Construct is linked to their Shardling." */
    static String situation(CardCatalog catalog, Side partnerSide) {
        return "My opponent's " + targetName(catalog) + " is linked to "
                + (partnerSide == Side.MINE ? "my " : "their ") + Cards.fillerName(catalog) + ".";
    }

    static String targetName(CardCatalog catalog) {
        return catalog.card(new CardId(TARGET)).name();
    }

    /** 8.11: a link across the board says why the asker's unit is involved. */
    static List<String> sideRules(Side partnerSide) {
        return partnerSide == Side.MINE ? List.of("8.11") : List.of();
    }
}
