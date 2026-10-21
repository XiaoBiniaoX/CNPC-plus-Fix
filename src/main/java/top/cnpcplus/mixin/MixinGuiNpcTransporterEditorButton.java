package top.cnpcplus.mixin;

import noppes.npcs.client.gui.roles.GuiNpcTransporter;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transport.client.GuiTransporterVisualEditor;

/**
 * 任务241：传送师（GuiNpcTransporter）职业页加「可视化编辑」入口按钮（id 50）。
 * 打开子页 GuiTransporterVisualEditor，直接改 public location（与原生同一实例），
 * 关子页后父页 save() 把 writeNBT（含 5 扩展键）经 SPacketTransportSave 落盘。
 */
@Mixin(value = GuiNpcTransporter.class, remap = false)
public abstract class MixinGuiNpcTransporterEditorButton {

    @Shadow(remap = false)
    public TransportLocation location;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addEditorButton(CallbackInfo ci) {
        GuiNpcTransporter self = (GuiNpcTransporter) (Object) this;
        self.addButton(new GuiButtonNop(self, 50,
                self.guiLeft + 4, self.guiTop + 56, 90, 20,
                net.minecraft.client.resources.language.I18n.get("cnpcplus.transporter.edit")));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$openEditor(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 50) return;
        ci.cancel();
        GuiNpcTransporter self = (GuiNpcTransporter) (Object) this;
        self.setSubGui(new GuiTransporterVisualEditor(this.location, self.npc.getId()));
    }
}
