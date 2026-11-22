package top.cnpcplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.packets.client.PacketSyncRecipeRemove;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.craftingview.RecipeIndexUpdates;

@Mixin(value = PacketSyncRecipeRemove.class, remap = false)
public abstract class MixinPacketSyncRecipeRemove {
    @Shadow @Final private ResourceLocation id;
    @Shadow @Final private int type;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$removeClientRecipe(CallbackInfo ci) {
        if (type != 6 && type != 7) return;
        ci.cancel();
        var client = Minecraft.getInstance();
        if (client.getSingleplayerServer() == null) {
            var controller = RecipeController.instance;
            controller.globalRecipes.remove(id);
            controller.anvilRecipes.remove(id);
        }
        RecipeIndexUpdates.replace(client.getConnection().getRecipeManager(), RecipeType.CRAFTING, id, null);
    }
}
