package top.cnpcplus.mixin;

import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.advanced.GuiNPCLinesMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.lines.client.GuiLinesArrayEditor;

@Mixin(value = GuiNPCLinesMenu.class, remap = false)
public class MixinGuiNPCLinesArraysMenu {
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$openArray(GuiButtonNop button, CallbackInfo ci) {
        int type = switch (button.id) { case 0 -> 2; case 1 -> 1; case 2 -> 0; case 5 -> 3; case 6 -> 4; case 7 -> 5; default -> -1; };
        if (type < 0) return;
        GuiNPCLinesMenu self = (GuiNPCLinesMenu)(Object)this;
        self.save();
        NoppesUtil.openGUI(self.player, new GuiLinesArrayEditor(self.npc, type));
        ci.cancel();
    }
}
