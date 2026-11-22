package top.cnpcplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import noppes.npcs.packets.client.PacketSyncRecipeUpdate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.craftingview.RecipeIndexUpdates;

@Mixin(value = PacketSyncRecipeUpdate.class, remap = false)
public abstract class MixinPacketSyncRecipeChange {
    @Shadow @Final private ResourceLocation id;
    @Shadow @Final private int type;
    @Shadow @Final private CompoundTag data;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$updateClientRecipe(CallbackInfo ci) {
        if (type != 6 && type != 7) return;
        ci.cancel();
        var client = Minecraft.getInstance();
        var connection = client.getConnection();
        RecipeCarpentry recipe = RecipeCarpentry.load(data);
        // The integrated server already updated the shared CNPC catalogue on its own thread.
        if (client.getSingleplayerServer() == null) {
            var controller = RecipeController.instance;
            var target = type == 6 ? controller.globalRecipes : controller.anvilRecipes;
            var other = type == 6 ? controller.anvilRecipes : controller.globalRecipes;
            other.remove(id);
            target.put(id, recipe);
        }
        RecipeIndexUpdates.replace(connection.getRecipeManager(), RecipeType.CRAFTING, this.id,
                type == 6 && recipe.isValid() ? recipe : null);
    }
}
