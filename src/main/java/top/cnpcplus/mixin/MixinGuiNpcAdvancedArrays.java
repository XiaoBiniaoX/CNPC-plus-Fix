package top.cnpcplus.mixin;

import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.lines.client.GuiNpcDialogArrays;
import top.cnpcplus.lines.client.GuiNpcSoundArrays;

@Mixin(value = GuiNpcAdvanced.class, remap = false)
public class MixinGuiNpcAdvancedArrays {
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$openArrays(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 10 && button.id != 11) return;
        GuiNpcAdvanced self = (GuiNpcAdvanced)(Object)this;
        self.save();
        NoppesUtil.openGUI(self.player, button.id == 10 ? new GuiNpcDialogArrays(self.npc) : new GuiNpcSoundArrays(self.npc));
        ci.cancel();
    }
}
