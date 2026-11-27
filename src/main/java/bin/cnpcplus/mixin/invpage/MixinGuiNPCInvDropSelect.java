package bin.cnpcplus.mixin.invpage;

import bin.cnpcplus.client.gui.SubGuiNpcOptionSelect;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNPCInv.class, remap = false)
public abstract class MixinGuiNPCInvDropSelect {
    @Shadow protected abstract void func_146284_a(GuiButton button);

    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$dropPickerButton(CallbackInfo ci) {
        GuiContainerNPCInterface gui = (GuiContainerNPCInterface) (Object) this;
        GuiNpcButton original = gui.getButton(10);
        if (original == null) return;
        original.setVisible(false);
        // 1.12.2 cycles option-array buttons before actionPerformed; the opener must be text-only.
        gui.addButton(new GuiNpcButton(31010, original.x, original.y, original.getWidth(), original.height,
                original.displayString + "…"));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openDropPicker(GuiButton button, CallbackInfo ci) {
        if (button.id != 31010) return;
        GuiContainerNPCInterface gui = (GuiContainerNPCInterface) (Object) this;
        gui.setSubGui(new SubGuiNpcOptionSelect(gui.getButton(10), "cnpcplus.select.dropMode", null, value -> {
            GuiNpcButton original = gui.getButton(10);
            original.setDisplay(value);
            func_146284_a(original);
            gui.initGui();
        }));
        ci.cancel();
    }
}
