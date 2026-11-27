package top.cnpcplus.mixin;

import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.resources.ResourceLocation;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 AbstractFurnaceBlockEntity 私有 recipeType 字段。 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccess {
    @Accessor("recipeType")
    RecipeType<? extends AbstractCookingRecipe> cnpcplus$getRecipeType();

    @Accessor("recipesUsed")
    Object2IntOpenHashMap<ResourceLocation> cnpcplus$getRecipesUsed();
}
