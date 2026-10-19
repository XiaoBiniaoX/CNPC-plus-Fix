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
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

@Mixin(value = GuiNPCFactionSetup.class, remap = false)
public abstract class MixinGuiNPCFactionInspection implements ITextfieldListener {
    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addInspectionButton(CallbackInfo ci) {
        GuiNPCFactionSetup self = (GuiNPCFactionSetup) (Object) this;
        boolean enabled = ((DamageInspectionAccess) self.npc.advanced).cnpcplus$isDamageInspectionEnabled();
        var existing = self.getButton(13);
        if (existing != null) return;
        self.addButton(new GuiButtonNop(self, 13, self.guiLeft + 4, self.guiTop + 91, 92, 20,
            Component.translatable(enabled ? "cnpcplus.inspection.enabled" : "cnpcplus.inspection.disabled").getString()));
        self.getButton(13).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("cnpcplus.inspection.tooltip")));
        self.addLabel(new noppes.npcs.shared.client.gui.components.GuiLabel(14,
            "cnpcplus.inspection.distance", self.guiLeft + 4, self.guiTop + 114));
        var distance = new noppes.npcs.shared.client.gui.components.GuiTextFieldNop(14, self,
            self.guiLeft + 98, self.guiTop + 110, 45, 20,
            Integer.toString(((top.cnpcplus.ai.DamageInspectionAccess) self.npc.advanced).cnpcplus$getInspectionDistance()));
        distance.numbersOnly = true;
        distance.setMinMaxDefault(1, 16, 3);
        self.addTextField(distance);
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$toggleInspection(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 13) return;
        GuiNPCFactionSetup self = (GuiNPCFactionSetup) (Object) this;
        DamageInspectionAccess data = (DamageInspectionAccess) self.npc.advanced;
        boolean enabled = !data.cnpcplus$isDamageInspectionEnabled();
        data.cnpcplus$setDamageInspectionEnabled(enabled);
        button.setMessage(Component.translatable(enabled ? "cnpcplus.inspection.enabled" : "cnpcplus.inspection.disabled"));
        Packets.sendServer(new SPacketMenuSave(EnumMenuType.ADVANCED, self.npc.advanced.save(new CompoundTag())));
        ci.cancel();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        if (field.id != 14) return;
        GuiNPCFactionSetup self = (GuiNPCFactionSetup) (Object) this;
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
