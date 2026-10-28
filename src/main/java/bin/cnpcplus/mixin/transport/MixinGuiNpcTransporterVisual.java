package bin.cnpcplus.mixin.transport;

import bin.cnpcplus.transport.client.TransportScreen;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.roles.GuiNpcTransporter;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcTransporter.class, remap = false)
public abstract class MixinGuiNpcTransporterVisual {
    @Inject(method = "func_73866_w_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$visualEditorButton(CallbackInfo ci) {
        GuiNpcTransporter self = (GuiNpcTransporter) (Object) this;
        GuiNpcButton button = new GuiNpcButton(50, self.guiLeft + 4, self.guiTop + 56, 120, 20, "cnpcplus.transport.edit");
        button.enabled = self.location.id >= 0;
        self.addButton(button);
    }
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openVisualEditor(GuiButton button, CallbackInfo ci) {
        if (button.id != 50) return;
        GuiNpcTransporter self = (GuiNpcTransporter) (Object) this;
        // 把原生页本身交给可视化编辑器：改名后由它回写原生页的名字输入框，
        // 否则原生页 save() 会拿打开时的旧名字把改动覆盖回去。
        self.setSubGui(new TransportScreen(self.npc.getEntityId(), true, self.location).parentGui(self));
        ci.cancel();
    }
}
