package bin.cnpcplus.mixin.bossbar;

import bin.cnpcplus.bossbar.client.SubGuiBossBar;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcDisplay.class, remap = false)
public abstract class MixinGuiNpcDisplayBossBar {
    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$entry(CallbackInfo ci) {
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        self.getButton(10).visible = false; self.getButton(12).visible = false; self.getLabel(12).enabled = false;
        self.addButton(new GuiNpcButton(420, self.guiLeft + 60, self.getButton(10).y, 110, 20, "selectServer.edit"));
    }
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$open(GuiButton button, CallbackInfo ci) {
        if (button.id != 420) return;
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        self.setSubGui(new SubGuiBossBar(self)); ci.cancel();
    }
}
