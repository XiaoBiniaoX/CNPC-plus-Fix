package top.cnpcplus.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.anvil.network.AnvilPacketHandler;
import top.cnpcplus.anvil.network.PacketOpenAnvilGui;

/**
 * 阶段231: 全局设置新增「可视化自定义铁砧配方」按钮。
 * 用户指定位置: 放在「可视化自定义锻造配方」正上方——即 CNPC 源码里
 * 「global.recipes」(id14, 第6行 guiTop+10+22*6) 的**右侧**,
 * 与锻造按钮 (第7行=22*7) / 熔炼按钮 (第8行=22*8) 同一右列模式: x=guiLeft+285, 宽100。
 * BTN=103（101=熔炼 102=锻造，独占不撞）。
 */
@Mixin(value = GuiNPCGlobalMainMenu.class, remap = false)
public class MixinGuiNPCGlobalMainMenuAnvil {

    private static final int BTN_ANVIL = 103;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addAnvilButton(CallbackInfo ci) {
        GuiNPCGlobalMainMenu self = (GuiNPCGlobalMainMenu) (Object) this;
        int x = self.guiLeft + 285;
        int y = self.guiTop + 10 + 22 * 6; // 第6行 = global.recipes 行; 锻造按钮(第7行)正上方
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_ANVIL, x, y, 100, 20, "cnpcplus.anvil.open"));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleAnvilButton(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != BTN_ANVIL) return;
        AnvilPacketHandler.CHANNEL.sendToServer(new PacketOpenAnvilGui(-1));
        ci.cancel();
    }
}
