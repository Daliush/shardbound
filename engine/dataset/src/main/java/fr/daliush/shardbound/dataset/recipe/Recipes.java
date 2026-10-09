package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.dataset.recipe.anchor.AnchorDoomHealRecipe;
import fr.daliush.shardbound.dataset.recipe.anchor.AnchorDoomRecipe;
import fr.daliush.shardbound.dataset.recipe.anchor.AnchorLinkRecipe;
import fr.daliush.shardbound.dataset.recipe.anchor.AnchorRemovalRecipe;
import fr.daliush.shardbound.dataset.recipe.anchor.AnchorSacrificeRecipe;
import java.util.List;

/** Every recipe of the dataset, one per interaction. */
public final class Recipes {

    private Recipes() {
    }

    public static List<Recipe> all() {
        return List.of(
                AnchorRemovalRecipe.destroy(),
                AnchorRemovalRecipe.returnToHand(),
                new AnchorSacrificeRecipe(),
                new AnchorDoomRecipe(),
                new AnchorDoomHealRecipe(),
                new AnchorLinkRecipe());
    }
}
