package top.cnpcplus.mixin;

import noppes.npcs.client.gui.roles.GuiNpcTransporter;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
    @Unique private String cnpcplus$pendingName = "";
    @Unique private int cnpcplus$pendingId = -1;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addEditorButton(CallbackInfo ci) {
        GuiNpcTransporter self = (GuiNpcTransporter) (Object) this;
        GuiButtonNop edit = new GuiButtonNop(self, 50,
                self.guiLeft + 4, self.guiTop + 56, 90, 20,
                net.minecraft.client.resources.language.I18n.get("cnpcplus.transporter.edit"));
        edit.active = this.location != null && this.location.id >= 0;
        self.addButton(edit);
        GuiTextFieldNop name = self.getTextField(0);
        if (name != null) {
            name.setMaxLength(64);
            name.setResponder(s -> {
                if (this.location == null || this.location.id < 0 || s == null || s.trim().isEmpty()) return;
                this.cnpcplus$pendingName = s.trim();
                this.cnpcplus$pendingId = this.location.id;
                this.location.name = this.cnpcplus$pendingName;
            });
        }
        if (self.getSubGui() instanceof GuiTransporterVisualEditor editor) editor.syncParentLocation();
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$openEditor(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 50) return;
        ci.cancel();
        GuiNpcTransporter self = (GuiNpcTransporter) (Object) this;
        if (this.location == null || this.location.id < 0) return;
        GuiTextFieldNop name = self.getTextField(0);
        if (name != null && !name.getValue().trim().isEmpty()) this.location.name = name.getValue().trim();
        self.setSubGui(new GuiTransporterVisualEditor(this.location, self.npc.getId()));
    }

    @Inject(method = "setGuiData", at = @At("TAIL"), remap = false)
    private void cnpcplus$restorePendingName(CompoundTag data, CallbackInfo ci) {
        GuiNpcTransporter self = (GuiNpcTransporter) (Object) this;
        if (this.location != null && this.location.id == this.cnpcplus$pendingId
                && !this.cnpcplus$pendingName.isEmpty()) {
            this.location.name = this.cnpcplus$pendingName;
            GuiTextFieldNop name = self.getTextField(0);
            if (name != null) name.setValue(this.cnpcplus$pendingName);
        }
        if (self.getSubGui() instanceof GuiTransporterVisualEditor editor) editor.syncParentLocation();
    }
}
