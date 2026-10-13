package top.cnpcplus.mixin;

import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * S4（High）：关界面时去掉客户端 npc.reset()。
 * 原版 close() 先客户端 reset 再发 SPacketMenuClose；服务端 handle 里还有一次
 * reset + 状态清理。客户端那次 reset 造成关界面瞬间跳变/交互状态丢失。
 * 服务端 SPacketMenuClose.handle 的 reset 独占该路径；客户端视觉状态由
 * 原版 synched entity data 自然回正。
 */
@Mixin(value = GuiNpcMenu.class, remap = false)
public abstract class MixinGuiNpcMenuCloseNoClientReset {

    @Redirect(method = "close", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;reset()V"), remap = false)
    private void cnpcplus$skipClientReset(EntityNPCInterface npc) {
        // 交由服务端 SPacketMenuClose.handle 执行 reset
    }
}
