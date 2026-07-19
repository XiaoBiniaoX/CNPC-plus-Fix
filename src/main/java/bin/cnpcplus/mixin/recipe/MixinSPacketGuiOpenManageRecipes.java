package bin.cnpcplus.mixin.recipe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.constants.EnumGuiType;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.server.SPacketGuiOpen;
import noppes.npcs.util.CustomNPCsScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores ManageRecipes open path: container buffer must be grid size (3/4), not npc entity id.
 * Upstream always wrote npc.getId() for every hasContainer GUI.
 */
@Mixin(SPacketGuiOpen.class)
public class MixinSPacketGuiOpenManageRecipes {

    @Inject(method = "sendOpenGui", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$manageRecipesOpen(Player player, EnumGuiType gui, EntityNPCInterface npc, BlockPos pos, CallbackInfo ci) {
        if (gui != EnumGuiType.ManageRecipes) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            ci.cancel();
            return;
        }
        NoppesUtilServer.setEditingNpc(player, npc);
        int size = pos == null ? 4 : pos.getX();
        if (size != 3 && size != 4) {
            size = 4;
        }
        final int grid = size;
        CustomNPCsScheduler.runTack(() -> {
            if (serverPlayer.getServer() == null) return;
            serverPlayer.getServer().submit(() ->
                    NoppesUtilServer.openContainerGui(serverPlayer, gui, buf -> buf.writeInt(grid)));
        }, 200);
        ci.cancel();
    }
}
