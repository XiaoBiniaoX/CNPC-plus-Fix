package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.sync.RecipeSync;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.packets.server.SPacketRecipesGet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores sendRecipeData: upstream 1.21.1 for-loops are empty so GUI list never fills.
 */
@Mixin(SPacketRecipesGet.class)
public class MixinSPacketRecipesGet {

    @Inject(method = "sendRecipeData", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$sendRecipeData(ServerPlayer player, int size, CallbackInfo ci) {
        RecipeSync.sendRecipeList(player, size);
        ci.cancel();
    }
}
