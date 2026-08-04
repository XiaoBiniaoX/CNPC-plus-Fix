package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketRecipeGet;
import noppes.npcs.packets.server.SPacketRecipeSave;
import noppes.npcs.packets.server.SPacketRecipesGet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SPacketRecipeSave.class, remap = false)
public class MixinSPacketRecipeSave {

    @Shadow private CompoundTag data;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$saveUseReturned(CallbackInfo ci) {
        ServerPlayer player = ((PacketServerBasic) (Object) this).player;
        if (player == null || this.data == null) {
            ci.cancel();
            return;
        }
        RecipeCarpentry recipe = RecipeCarpentry.load(this.data);
        RecipeCarpentry saved = RecipeController.instance.saveRecipe(recipe);
        // 3 = global list, 4 = anvil list (matches SPacketRecipesGet)
        SPacketRecipesGet.sendRecipeData(player, saved.isGlobal ? 3 : 4);
        SPacketRecipeGet.setRecipeGui(player, saved);
        ci.cancel();
    }
}
