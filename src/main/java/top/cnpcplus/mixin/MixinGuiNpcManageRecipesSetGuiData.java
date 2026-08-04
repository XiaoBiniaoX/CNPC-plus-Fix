package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesSetGuiData {

    @Shadow(remap = false)
    private ContainerManageRecipes container;

    @Inject(method = "setGuiData", at = @At("RETURN"), remap = false)
    private void cnpcplus$onSetGuiData(CompoundTag compound, CallbackInfo ci) {
        RecipeCarpentry recipe = RecipeCarpentry.load(compound);
        ResourceLocation id = recipe.getId();
        top.cnpcplus.craftingview.RecipeGridSnapshot.tryRestore(this.container, id);
    }
}
