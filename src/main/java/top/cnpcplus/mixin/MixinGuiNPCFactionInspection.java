package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.advanced.GuiNPCFactionSetup;
import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketMenuSave;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.DamageInspectionAccess;
import top.cnpcplus.ai.UntargetableAccess;
import top.cnpcplus.ai.PursuitLimit;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

@Mixin(value = GuiNPCFactionSetup.class, remap = false)
public abstract class MixinGuiNPCFactionInspection implements ITextfieldListener {
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void cnpcplus$addInspectionButton(CallbackInfo ci) {
        GuiNPCFactionSetup self = (GuiNPCFactionSetup) (Object) this;
        if (self.getButton(13) == null) {
            boolean enabled = ((DamageInspectionAccess) self.npc.advanced).cnpcplus$isDamageInspectionEnabled();
            self.addButton(new GuiButtonNop(self, 13, self.guiLeft + 4, self.guiTop + 91, 92, 20,
                Component.translatable(enabled ? "cnpcplus.inspection.enabled" : "cnpcplus.inspection.disabled").getString()));
            self.getButton(13).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("cnpcplus.inspection.tooltip")));
            self.addLabel(new noppes.npcs.shared.client.gui.components.GuiLabel(14,
                "cnpcplus.inspection.distance", self.guiLeft + 4, self.guiTop + 114));
            var distance = new noppes.npcs.shared.client.gui.components.GuiTextFieldNop(14, self,
                self.guiLeft + 98, self.guiTop + 110, 45, 20,
                Integer.toString(((DamageInspectionAccess) self.npc.advanced).cnpcplus$getInspectionDistance()));
            distance.numbersOnly = true;
            distance.setMinMaxDefault(1, 16, 3);
            self.addTextField(distance);
        }
        if (self.getButton(16) == null) {
            boolean untargetable = ((UntargetableAccess) self.npc.advanced).cnpcplus$isUntargetable();
            self.addLabel(new noppes.npcs.shared.client.gui.components.GuiLabel(15,
                "cnpcplus.untargetable", self.guiLeft + 4, self.guiTop + 142));
            self.addButton(new GuiButtonNop(self, 16, self.guiLeft + 144, self.guiTop + 137, 40, 20,
                new String[]{"gui.no", "gui.yes"}, untargetable ? 1 : 0));
            self.getButton(16).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("cnpcplus.untargetable.tooltip")));
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$addPursuit(CallbackInfo ci) {
        GuiNPCFactionSetup self = (GuiNPCFactionSetup)(Object)this;
        PursuitLimit pursuit = (PursuitLimit)self.npc.advanced;
        self.addLabel(new noppes.npcs.shared.client.gui.components.GuiLabel(17,
                "cnpcplus.pursuit.distance", self.guiLeft + 4, self.guiTop + 166));
        var radius = new GuiTextFieldNop(17, self, self.guiLeft + 98, self.guiTop + 161, 44, 20,
                Float.toString(pursuit.cnpcplus$getPursuitRadius()));
        radius.setFloatsOnly().setMinMaxDefault(0.0F, 32.0F, 32.0F);
        self.addTextField(radius);
        self.addButton(new GuiButtonNop(self, 18, self.guiLeft + 144, self.guiTop + 161, 40, 20,
                new String[]{"gui.no", "gui.yes"}, pursuit.cnpcplus$isPursuitLimited() ? 1 : 0));
        self.getButton(18).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cnpcplus.pursuit.tooltip")));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$toggleInspection(GuiButtonNop button, CallbackInfo ci) {
        GuiNPCFactionSetup self = (GuiNPCFactionSetup) (Object) this;
        if (button.id == 13) {
            DamageInspectionAccess data = (DamageInspectionAccess) self.npc.advanced;
            boolean enabled = !data.cnpcplus$isDamageInspectionEnabled();
            data.cnpcplus$setDamageInspectionEnabled(enabled);
            button.setMessage(Component.translatable(enabled ? "cnpcplus.inspection.enabled" : "cnpcplus.inspection.disabled"));
        } else if (button.id == 16) {
            ((UntargetableAccess) self.npc.advanced).cnpcplus$setUntargetable(button.getValue() == 1);
        } else if (button.id == 18) {
            GuiTextFieldNop.unfocus();
            ((PursuitLimit)self.npc.advanced).cnpcplus$setPursuitLimited(button.getValue() == 1);
        } else {
            return;
        }
        Packets.sendServer(new SPacketMenuSave(EnumMenuType.ADVANCED, self.npc.advanced.save(new CompoundTag())));
        ci.cancel();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        GuiNPCFactionSetup self = (GuiNPCFactionSetup) (Object) this;
        if (field.id == 17) {
            PursuitLimit pursuit = (PursuitLimit)self.npc.advanced;
            if (field.isFloat()) pursuit.cnpcplus$setPursuitRadius(field.getFloat());
            field.setValue(Float.toString(pursuit.cnpcplus$getPursuitRadius()));
            Packets.sendServer(new SPacketMenuSave(EnumMenuType.ADVANCED, self.npc.advanced.save(new CompoundTag())));
            return;
        }
        if (field.id != 14) return;
        try {
            ((DamageInspectionAccess) self.npc.advanced).cnpcplus$setInspectionDistance(
                Math.max(1, Math.min(16, Integer.parseInt(field.getValue()))));
        } catch (NumberFormatException ignored) {
            ((DamageInspectionAccess) self.npc.advanced).cnpcplus$setInspectionDistance(3);
            field.setValue("3");
        }
        Packets.sendServer(new SPacketMenuSave(EnumMenuType.ADVANCED, self.npc.advanced.save(new CompoundTag())));
    }
}
