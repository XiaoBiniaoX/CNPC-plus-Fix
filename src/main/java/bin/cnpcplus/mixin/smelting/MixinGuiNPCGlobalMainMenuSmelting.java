package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.network.PacketSmeltingAction;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNPCGlobalMainMenu.class, remap = false)
public final class MixinGuiNPCGlobalMainMenuSmelting {
    private static final int BUTTON = 101;

    @Inject(method = "init", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$addButton(CallbackInfo ci) {
        GuiNPCGlobalMainMenu menu = (GuiNPCGlobalMainMenu) (Object) this;
        // 与 1.20.1 完全一致：放在现有 200 宽按钮列右侧，不占中央和底部。
        menu.addButton(new GuiButtonNop((IGuiInterface) menu, BUTTON, menu.guiLeft + 285,
                menu.guiTop + 186, 100, 20, "cnpcplus.smelting.open"));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$open(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != BUTTON) return;
        PacketDistributor.sendToServer(new PacketSmeltingAction(0, -1, "", 200.0F, 0.0F, false, false, false));
        ci.cancel();
    }
}
