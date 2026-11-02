package top.cnpcplus.mixin;

import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.client.gui.roles.GuiNpcSpawner;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiMenuTopButton;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S4（High）：关界面时去掉客户端 npc.reset()。
 * 原版 close() 先客户端 reset 再发 SPacketMenuClose；服务端 handle 里还有一次
 * reset + 状态清理。客户端那次 reset 造成关界面瞬间跳变/交互状态丢失。
 * 服务端 SPacketMenuClose.handle 的 reset 独占该路径；客户端视觉状态由
 * 原版 synched entity data 自然回正。
 */
@Mixin(value = GuiNpcMenu.class, remap = false)
public abstract class MixinGuiNpcMenuCloseNoClientReset {
    @Shadow private IGuiInterface parent;
    @Shadow private GuiMenuTopButton[] topButtons;

    @Inject(method = "initGui", at = @At("TAIL"), require = 1)
    private void cnpcplus$fitSummonerMenu(int left, int top, int width, CallbackInfo ci) {
        if (!(parent instanceof GuiNpcSpawner)) return;
        int total = 0;
        for (var button : topButtons) total += button.getWidth() + 1;
        if (total <= width - 8) return;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int x = left + 4, right = left + width - 4;
        for (var button : topButtons) {
            button.setWidth(font.width(button.getMessage()) + 4);
            if (button.id == 0 || button.id == 66) {
                right -= button.getWidth();
                button.setX(right);
                right--;
            } else {
                button.setX(x);
                x += button.getWidth() + 1;
            }
        }
    }

    @Redirect(method = "close", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;reset()V"), remap = false)
    private void cnpcplus$skipClientReset(EntityNPCInterface npc) {
        // 交由服务端 SPacketMenuClose.handle 执行 reset
    }
}
