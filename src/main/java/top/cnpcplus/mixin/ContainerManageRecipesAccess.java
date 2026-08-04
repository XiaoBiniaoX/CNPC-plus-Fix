package top.cnpcplus.mixin;

import net.minecraft.world.SimpleContainer;
import noppes.npcs.containers.ContainerManageRecipes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ContainerManageRecipes.class)
public interface ContainerManageRecipesAccess {
    @Accessor(value = "craftingMatrix", remap = false)
    SimpleContainer cnpcplus$getCraftingMatrix();
}
