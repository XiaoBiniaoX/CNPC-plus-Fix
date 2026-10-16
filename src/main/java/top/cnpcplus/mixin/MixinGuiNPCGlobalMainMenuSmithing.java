package top.cnpcplus.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.smithing.network.PacketOpenSmithingGui;
import top.cnpcplus.smithing.network.SmithingPacketHandler;

/**
 * 阶段226: 全局设置新增「可视化自定义锻造配方」按钮。
 * 用户指定位置: 放在「可视化自定义熔炼配方」正上方——即 CNPC 源码里
 * 「Npc自然生成设置」(id15, 第7行 guiTop+10+22*7, guiLeft+85 宽200) 的**右侧**,
 * 与熔炼按钮 (第8行右列) 同一位置模式: x=guiLeft+285, 宽100。
 */
@Mixin(value = GuiNPCGlobalMainMenu.class, remap = false)
public class MixinGuiNPCGlobalMainMenuSmithing {

    private static final int BTN_SMITHING = 102;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addSmithingButton(CallbackInfo ci) {
        GuiNPCGlobalMainMenu self = (GuiNPCGlobalMainMenu) (Object) this;
        int x = self.guiLeft + 285;
        int y = self.guiTop + 10 + 22 * 7; // 第7行 = 原版「自然生成设置」右侧; 熔炼按钮(第8行)正上方
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_SMITHING, x, y, 100, 20, "cnpcplus.smithing.open"));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleSmithingButton(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != BTN_SMITHING) return;
        SmithingPacketHandler.CHANNEL.sendToServer(new PacketOpenSmithingGui(-1));
        ci.cancel();
    }
}
