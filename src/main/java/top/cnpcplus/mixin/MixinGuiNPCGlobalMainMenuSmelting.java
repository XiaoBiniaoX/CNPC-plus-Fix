package top.cnpcplus.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.smelting.network.PacketOpenSmeltingGui;
import top.cnpcplus.smelting.network.SmeltingPacketHandler;

/**
 * B3: 全局设置新增「可视化自定义熔炼配方」按钮。
 * 布局要求：宽度 = 现有按钮(默认200)的一半(100)，放右侧（guiLeft+85 列右侧、guiLeft+320 附近），不占中间。
 */
@Mixin(value = GuiNPCGlobalMainMenu.class, remap = false)
public class MixinGuiNPCGlobalMainMenuSmelting {

    private static final int BTN_SMELTING = 101;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addSmeltingButton(CallbackInfo ci) {
        GuiNPCGlobalMainMenu self = (GuiNPCGlobalMainMenu) (Object) this;
        // 现有按钮列在 guiLeft+85 宽 200，右侧留空；放 guiLeft+85+200=285 处，宽 100
        int x = self.guiLeft + 285;
        int y = self.guiTop + 10 + 22 * 8; // 与最后一行的位置对齐（避开中间）
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_SMELTING, x, y, 100, 20, "cnpcplus.smelting.open"));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleSmeltingButton(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != BTN_SMELTING) return;
        SmeltingPacketHandler.CHANNEL.sendToServer(new PacketOpenSmeltingGui(-1));
        ci.cancel();
    }
}
