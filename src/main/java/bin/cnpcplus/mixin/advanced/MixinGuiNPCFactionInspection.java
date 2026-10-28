package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.common.IDamageInspectionAccess;
import bin.cnpcplus.common.IUntargetableAccess;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.Client;
import noppes.npcs.client.gui.advanced.GuiNPCFactionSetup;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 238-12/13：阵营设置页加「视察」开关、观察距离与「不会被 NPC 作为攻击目标」。
 * 布局沿用 1.20.1：y91 视察按钮、y110 距离输入、y142 不可仇恨。
 */
@Mixin(value = GuiNPCFactionSetup.class, remap = false)
public abstract class MixinGuiNPCFactionInspection implements ITextfieldListener {

    @Unique
    private static final int CNPCPLUS_BTN_INSPECTION = 13;

    @Unique
    private static final int CNPCPLUS_TF_DISTANCE = 14;

    @Unique
    private static final int CNPCPLUS_LABEL_UNTARGETABLE = 15;

    @Unique
    private static final int CNPCPLUS_BTN_UNTARGETABLE = 16;

    @Unique
    private DataAdvanced cnpcplus$advanced() {
        return ((GuiNPCFactionSetup) (Object) this).npc.advanced;
    }

    @Inject(method = "func_73866_w_", at = @At("TAIL"))
    private void cnpcplus$addInspectionAndUntargetable(CallbackInfo ci) {
        GuiNPCFactionSetup self = (GuiNPCFactionSetup) (Object) this;
        if (self.getButton(CNPCPLUS_BTN_INSPECTION) == null) {
            IDamageInspectionAccess data = (IDamageInspectionAccess) this.cnpcplus$advanced();
            boolean enabled = data.cnpcplus$isDamageInspectionEnabled();
            self.addButton(new GuiNpcButton(CNPCPLUS_BTN_INSPECTION,
                    self.guiLeft + 4, self.guiTop + 91, 92, 20,
                    enabled ? "cnpcplus.inspection.enabled" : "cnpcplus.inspection.disabled"));
            self.addLabel(new GuiNpcLabel(CNPCPLUS_TF_DISTANCE, "cnpcplus.inspection.distance",
                    self.guiLeft + 4, self.guiTop + 114));
            GuiNpcTextField distance = new GuiNpcTextField(CNPCPLUS_TF_DISTANCE, self,
                    self.getFontRenderer(), self.guiLeft + 98, self.guiTop + 110, 45, 20,
                    Integer.toString(data.cnpcplus$getInspectionDistance()));
            distance.numbersOnly = true;
            distance.setMinMaxDefault(1, 16, 3);
            self.addTextField(distance);
        }

        if (self.getButton(CNPCPLUS_BTN_UNTARGETABLE) == null) {
            IUntargetableAccess data = (IUntargetableAccess) this.cnpcplus$advanced();
            self.addLabel(new GuiNpcLabel(CNPCPLUS_LABEL_UNTARGETABLE, "cnpcplus.untargetable",
                    self.guiLeft + 4, self.guiTop + 142));
            self.addButton(new GuiNpcButton(CNPCPLUS_BTN_UNTARGETABLE,
                    self.guiLeft + 144, self.guiTop + 137, 40, 20,
                    new String[]{"gui.no", "gui.yes"}, data.cnpcplus$isUntargetable() ? 1 : 0));
        }
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$toggle(GuiButton button, CallbackInfo ci) {
        if (button == null) return;
        if (button.id == CNPCPLUS_BTN_INSPECTION) {
            IDamageInspectionAccess data = (IDamageInspectionAccess) this.cnpcplus$advanced();
            boolean enabled = !data.cnpcplus$isDamageInspectionEnabled();
            data.cnpcplus$setDamageInspectionEnabled(enabled);
            ((GuiNpcButton) button).setDisplayText(
                    enabled ? "cnpcplus.inspection.enabled" : "cnpcplus.inspection.disabled");
        } else if (button.id == CNPCPLUS_BTN_UNTARGETABLE) {
            ((IUntargetableAccess) this.cnpcplus$advanced())
                    .cnpcplus$setUntargetable(((GuiNpcButton) button).getValue() == 1);
        } else {
            return;
        }
        Client.sendData(EnumPacketServer.MainmenuAdvancedSave,
                this.cnpcplus$advanced().writeToNBT(new NBTTagCompound()));
        ci.cancel();
    }

    @Override
    public void unFocused(GuiNpcTextField textfield) {
        GuiNPCFactionSetup self = (GuiNPCFactionSetup) (Object) this;
        if (textfield == null || self.getTextField(CNPCPLUS_TF_DISTANCE) != textfield) return;
        ((IDamageInspectionAccess) this.cnpcplus$advanced())
                .cnpcplus$setInspectionDistance(textfield.getInteger());
        Client.sendData(EnumPacketServer.MainmenuAdvancedSave,
                this.cnpcplus$advanced().writeToNBT(new NBTTagCompound()));
    }
}
