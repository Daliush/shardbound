package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.dataset.recipe.anchor.AnchorDoomHealRecipe;
import fr.daliush.shardbound.dataset.recipe.anchor.AnchorDoomRecipe;
import fr.daliush.shardbound.dataset.recipe.anchor.AnchorLinkRecipe;
import fr.daliush.shardbound.dataset.recipe.anchor.AnchorRemovalRecipe;
import fr.daliush.shardbound.dataset.recipe.anchor.AnchorSacrificeRecipe;
import fr.daliush.shardbound.dataset.recipe.echo.EchoChainRecipe;
import fr.daliush.shardbound.dataset.recipe.echo.EchoDeathRecipe;
import fr.daliush.shardbound.dataset.recipe.echo.EchoModifyRecipe;
import fr.daliush.shardbound.dataset.recipe.echo.EchoReturnToHandRecipe;
import fr.daliush.shardbound.dataset.recipe.fracture.FractureLostProgressRecipe;
import fr.daliush.shardbound.dataset.recipe.fracture.FractureStepsRecipe;
import fr.daliush.shardbound.dataset.recipe.general.AuraDamageRecipe;
import fr.daliush.shardbound.dataset.recipe.general.DeathTriggerOrderRecipe;
import fr.daliush.shardbound.dataset.recipe.general.FreezeInterceptRecipe;
import fr.daliush.shardbound.dataset.recipe.general.RecallRecipe;
import fr.daliush.shardbound.dataset.recipe.link.LinkDamageRecipe;
import fr.daliush.shardbound.dataset.recipe.link.LinkNoShareRecipe;
import fr.daliush.shardbound.dataset.recipe.overcharge.OverchargeAttackCostRecipe;
import fr.daliush.shardbound.dataset.recipe.overcharge.OverchargeLockRecipe;
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
                new AnchorLinkRecipe(),
                new EchoDeathRecipe(),
                new EchoReturnToHandRecipe(),
                new EchoChainRecipe(),
                new EchoModifyRecipe(),
                new LinkDamageRecipe(),
                new LinkNoShareRecipe(),
                new FractureStepsRecipe(),
                new FractureLostProgressRecipe(),
                new OverchargeLockRecipe(),
                new OverchargeAttackCostRecipe(),
                new DeathTriggerOrderRecipe(),
                new FreezeInterceptRecipe(),
                new AuraDamageRecipe(),
                new RecallRecipe());
    }
}
