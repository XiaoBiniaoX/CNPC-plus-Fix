package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.sync.RecipeSync;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.NoppesUtilServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoppesUtilServer.class)
public class MixinNoppesUtilServerRecipes {

    @Inject(method = "sendRecipeData", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplusSendRecipeData(EntityPlayerMP player, int size, CallbackInfo ci) {
        RecipeSync.sendRecipeList(player, size);
        ci.cancel();
    }
}
