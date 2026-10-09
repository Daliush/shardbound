package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.content.CardCatalog;
import java.util.List;

/**
 * Builds the scenarios of one interaction (design doc §6.1). The engine plays them; the events each scenario selects
 * give its answer key: their sentences are the key facts, their rules the essential rules.
 */
public interface Recipe {

    /** "anchor+destroy": every scenario of an interaction sits on the same side of the split. */
    String interaction();

    Category category();

    /** One scenario per combination of the cards that fit and the recipe's variants. */
    List<Scenario> scenarios(CardCatalog catalog);
}
