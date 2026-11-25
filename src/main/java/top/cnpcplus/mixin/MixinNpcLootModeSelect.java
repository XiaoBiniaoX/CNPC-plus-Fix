package top.cnpcplus.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.SubGuiNpcOptionSelect;

@Mixin(value = GuiNPCInv.class, remap = false)
public abstract class MixinNpcLootModeSelect {
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/shared/client/gui/components/GuiButtonNop;<init>(Lnoppes/npcs/shared/client/gui/listeners/IGuiInterface;IIIII[Ljava/lang/String;I)V"), index = 6, require = 1)
    private String[] cnpcplus$lootModes(String[] original) {
        return new String[]{"inv.scatter", "inv.auto", "inv.point", "inv.spread", "inv.explode"};
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
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
