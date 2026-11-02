package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.client.GuiNpcDialogArrays;
import bin.cnpcplus.lines.client.GuiNpcSoundArrays;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcAdvanced.class, remap = false)
public abstract class MixinGuiNpcAdvancedArrays {
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openArrays(GuiButton button, CallbackInfo ci) {
        if (button.id != 10 && button.id != 11) return;
        GuiNpcAdvanced self = (GuiNpcAdvanced) (Object) this;
        self.save();
        NoppesUtil.openGUI(self.player, button.id == 10 ? new GuiNpcDialogArrays(self.npc) : new GuiNpcSoundArrays(self.npc));
        ci.cancel();
    }
}
