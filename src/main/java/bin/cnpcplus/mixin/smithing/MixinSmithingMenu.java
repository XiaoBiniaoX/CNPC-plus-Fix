package bin.cnpcplus.mixin.smithing;

import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import top.cnpcplus.smithing.SmithingMenuAccess;

import java.util.List;

@Mixin(value = SmithingMenu.class, remap = false)
public abstract class MixinSmithingMenu implements SmithingMenuAccess {
    @Shadow @Final private Level level;
    @Shadow @Final @Mutable private List<RecipeHolder<SmithingRecipe>> recipes;
    @Shadow private RecipeHolder<SmithingRecipe> selectedRecipe;
    @Shadow public abstract void createResult();

    @Override
    public void cnpcplus$refreshRecipes() {
        this.recipes = this.level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING);
        this.selectedRecipe = null;
        this.createResult();
    }
}
