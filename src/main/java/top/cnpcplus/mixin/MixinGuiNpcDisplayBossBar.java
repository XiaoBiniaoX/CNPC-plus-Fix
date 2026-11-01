package top.cnpcplus.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.bossbar.client.SubGuiBossBar;

@Mixin(value = GuiNpcDisplay.class, remap = false)
public abstract class MixinGuiNpcDisplayBossBar {
    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$bossEntry(CallbackInfo ci) {
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        GuiButtonNop mode = self.getButton(10), color = self.getButton(12);
        mode.visible = mode.active = false;
        color.visible = color.active = false;
        self.wrapper.labels.remove(12);
        self.addButton(new GuiButtonNop(self, 420, mode.getX(), mode.getY(), 110, 20, "selectServer.edit"));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openBossPanel(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 420) return;
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        self.setSubGui(new SubGuiBossBar(self));
        ci.cancel();
    }
}
