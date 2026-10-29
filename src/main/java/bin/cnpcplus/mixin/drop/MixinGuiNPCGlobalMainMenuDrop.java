package bin.cnpcplus.mixin.drop;

import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.neoforged.neoforge.network.PacketDistributor;
import top.cnpcplus.drop.network.PacketOpenDropGui;

/**
 * 阶段232: 全局设置新增「可视化自定义掉落物配方」按钮。
 * 用户指定位置: 放在「可视化自定义铁砧配方」正上方——即铁砧按钮 (第6行 guiTop+10+22*6)
 * 的**上一行** n=5: x=guiLeft+285, 宽100, 与铁砧/锻造/熔炼同一右列模式。
 * BTN=104（101=熔炼 102=锻造 103=铁砧，独占不撞）。
 */
@Mixin(value = GuiNPCGlobalMainMenu.class, remap = false)
public class MixinGuiNPCGlobalMainMenuDrop {

    private static final int BTN_DROP = 104;

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void cnpcplus$addDropButton(CallbackInfo ci) {
        GuiNPCGlobalMainMenu self = (GuiNPCGlobalMainMenu) (Object) this;
        int x = self.guiLeft + 285;
        int y = self.guiTop + 10 + 22 * 5; // 第5行 = 铁砧按钮(第6行)正上方
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_DROP, x, y, 100, 20, "cnpcplus.drop.open"));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleDropButton(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != BTN_DROP) return;
        PacketDistributor.sendToServer(new PacketOpenDropGui(-1));
        ci.cancel();
    }
}
