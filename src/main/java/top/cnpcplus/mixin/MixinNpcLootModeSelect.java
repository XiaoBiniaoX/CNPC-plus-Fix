package top.cnpcplus.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.SubGuiNpcOptionSelect;

@Mixin(value = GuiNPCInv.class, remap = false)
public abstract class MixinNpcLootModeSelect {
    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$lootPicker(CallbackInfo ci) {
        GuiNPCInv self = (GuiNPCInv)(Object)this;
        GuiButtonNop original = self.getButton(10);
        original.visible = false;
        original.shown = false;
        self.addButton(new GuiButtonNop(self, 10, original.getX(), original.getY(), original.getWidth(), original.getHeight(),
                original.getMessage().getString() + "…", pressed -> self.setSubGui(
                new SubGuiNpcOptionSelect(self, original, "cnpcplus.select.loot", null, null))));
    }
}
