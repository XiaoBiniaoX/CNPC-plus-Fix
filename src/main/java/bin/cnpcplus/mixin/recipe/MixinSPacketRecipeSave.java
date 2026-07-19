package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.RecipeControllerFacade;
import bin.cnpcplus.recipe.RecipeNbtKeys;
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

/**
 * Pass CnpcPlusSyncId so rename updates the same recipe identity.
 * player is on PacketServerBasic (parent), not SPacketRecipeSave — do not @Shadow it here.
 */
@Mixin(SPacketRecipeSave.class)
public class MixinSPacketRecipeSave {

    @Shadow(remap = false)
    private CompoundTag data;

    @Inject(method = "handle()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusHandle(CallbackInfo ci) {
        ServerPlayer sp = ((PacketServerBasic) (Object) this).player;
        if (sp == null || this.data == null || RecipeController.instance == null) {
            return;
        }
        RecipeCarpentry recipe = RecipeCarpentry.load(this.data, sp.registryAccess());
        if (this.data.contains("Name")) {
            recipe.name = this.data.getString("Name");
        }
        if (recipe.name == null || recipe.name.isEmpty()) {
            recipe.name = "unnamed";
        }
        int syncId = this.data.contains(RecipeNbtKeys.SYNC_ID) ? this.data.getInt(RecipeNbtKeys.SYNC_ID) : -1;
        recipe = RecipeControllerFacade.saveRecipe(recipe, RecipeController.instance, syncId);
        SPacketRecipesGet.sendRecipeData(sp, recipe.isGlobal ? 3 : 4);
        SPacketRecipeGet.setRecipeGui(sp, recipe);
        ci.cancel();
    }
}