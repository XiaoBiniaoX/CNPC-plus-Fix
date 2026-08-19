package top.cnpcplus.mixin;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** 暴露 RecipeManager 私有表，供动态注册/移除自定义熔炼配方（服务端权威层）。 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccess {
    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> cnpcplus$getRecipes();

    @Accessor("recipes")
    void cnpcplus$setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes);

    @Accessor("byName")
    Map<ResourceLocation, Recipe<?>> cnpcplus$getByName();

    @Accessor("byName")
    void cnpcplus$setByName(Map<ResourceLocation, Recipe<?>> byName);
}
