package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.dataset.recipe.anchor.AnchorDestroyRecipe;
import java.util.List;

/** Every recipe of the dataset, one per interaction. */
public final class Recipes {

    private Recipes() {
    }

    public static List<Recipe> all() {
        return List.of(new AnchorDestroyRecipe());
    }
}
