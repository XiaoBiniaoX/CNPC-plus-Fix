package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.RecipeControllerFacade;
import bin.cnpcplus.recipe.RecipeNbtKeys;
import bin.cnpcplus.recipe.id.RecipeIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.PacketHandlerServer;
import noppes.npcs.Server;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercept RecipeSave to honor CnpcPlusSyncId (rename-safe identity).
 */
@Mixin(PacketHandlerServer.class)
public class MixinPacketHandlerServerRecipes {

    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusHandle(EnumPacketServer type, ByteBuf buffer, EntityPlayerMP player, EntityNPCInterface npc, CallbackInfo ci) {
        if (type != EnumPacketServer.RecipeSave) return;
        try {
            NBTTagCompound nbt = Server.readNBT(buffer);
            RecipeCarpentry recipe = RecipeCarpentry.read(nbt);
            if (recipe.name == null || recipe.name.isEmpty()) {
                if (nbt.hasKey("Name")) recipe.name = nbt.getString("Name");
            }
            int preferred = -1;
            if (nbt.hasKey(RecipeNbtKeys.SYNC_ID)) {
                preferred = nbt.getInteger(RecipeNbtKeys.SYNC_ID);
            } else if (recipe.id > 0) {
                preferred = recipe.id;
            }
            RecipeController controller = RecipeController.instance;
            if (controller == null) return;
            RecipeCarpentry saved = RecipeControllerFacade.saveRecipe(recipe, controller, preferred);
            NoppesUtilServer.sendRecipeData(player, saved.isGlobal ? 3 : 4);
            NoppesUtilServer.setRecipeGui(player, saved);
            CnpcPlus.LOGGER.info("[Packet] RecipeSave name={} id={} preferred={}",
                    saved.name, Integer.valueOf(saved.id), Integer.valueOf(preferred));
            ci.cancel();
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[Packet] RecipeSave failed", e);
        }
    }
}
