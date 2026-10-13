package bin.cnpcplus.mixin.invpage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.CustomNpcs;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.constants.EnumGuiType;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进入物品栏页顿挫修复（2026-10-14 用户反馈）。
 * 原版切物品栏走 requestOpenGUI → sendOpenGui → runTack(..., 200) 硬编码
 * 200ms 延迟后才 player.openGui；其他主菜单页走 proxy.openGui 纯客户端秒切。
 * 本 mixin 在 sendOpenGui HEAD 拦截 MainMenuInv，跳过 200ms 延迟直接 openGui。
 * setEditingNpc 仍调用（同步发 EDIT_NPC 包）；同一连接包有序，OpenGui 必在
 * EDIT_NPC 之后到达客户端，lastNpc 已就绪。sendExtraData 对 MainMenuInv 无操作
 * （仅 Player* 页发数据），可安全跳过。
 */
@Mixin(NoppesUtilServer.class)
public class MixinNoppesUtilServerInvFastOpen {

    @Inject(method = "sendOpenGui(Lnet/minecraft/entity/player/EntityPlayer;Lnoppes/npcs/constants/EnumGuiType;Lnoppes/npcs/entity/EntityNPCInterface;III)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$fastOpenInventory(EntityPlayer player, EnumGuiType gui, EntityNPCInterface npc, int x, int y, int z, CallbackInfo ci) {
        if (gui != EnumGuiType.MainMenuInv) {
            return;
        }
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        NoppesUtilServer.setEditingNpc(player, npc);
        player.openGui(CustomNpcs.instance, gui.ordinal(), player.world, x, y, z);
        ci.cancel();
    }
}
