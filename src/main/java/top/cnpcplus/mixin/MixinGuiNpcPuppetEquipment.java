package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.roles.GuiNpcPuppet;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.roles.JobPuppet;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.puppet.JobPuppetAccessor;
import top.cnpcplus.puppet.PartConfigAccessor;

import java.util.HashMap;

@Mixin(value = GuiNpcPuppet.class)
public abstract class MixinGuiNpcPuppetEquipment extends GuiNPCInterface implements ITextfieldListener {
    @Shadow(remap = false) private JobPuppet job;
    @Shadow(remap = false) private String selectedName;
    @Shadow(remap = false) private boolean isStart;
    @Shadow(remap = false) public HashMap<String, JobPuppet.PartConfig> data;

    @Unique
    private static final String[] CNPCPLUS_EQUIP_PARTS = {"主手物品", "副手物品", "头盔", "甲胄", "护腿", "靴子"};

    @Inject(method = "m_7856_", at = @At(value = "FIELD", target = "Lnoppes/npcs/client/gui/roles/GuiNpcPuppet;data:Ljava/util/HashMap;", shift = At.Shift.AFTER), remap = false)
    private void cnpcplus$addEquipmentParts(CallbackInfo ci) {
        JobPuppetAccessor acc = (JobPuppetAccessor) this.job;
        JobPuppet.PartConfig[] configs = this.isStart ? new JobPuppet.PartConfig[]{
                acc.cnpcplus$getMainhand(), acc.cnpcplus$getOffhand(), acc.cnpcplus$getHelmet(),
                acc.cnpcplus$getChestplate(), acc.cnpcplus$getLeggings(), acc.cnpcplus$getBoots()
        } : new JobPuppet.PartConfig[]{
                acc.cnpcplus$getMainhand2(), acc.cnpcplus$getOffhand2(), acc.cnpcplus$getHelmet2(),
                acc.cnpcplus$getChestplate2(), acc.cnpcplus$getLeggings2(), acc.cnpcplus$getBoots2()
        };
        for (int i = 0; i < CNPCPLUS_EQUIP_PARTS.length; i++) {
            this.data.put(CNPCPLUS_EQUIP_PARTS[i], configs[i]);
        }
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addOffsetFields(CallbackInfo ci) {
        if (this.selectedName == null) return;
        JobPuppet.PartConfig config = this.data.get(this.selectedName);
        if (!(config instanceof PartConfigAccessor acc)) return;

        this.addLabel(new GuiLabel(110, "X", this.guiLeft + 175, this.guiTop + 19, 0xFFFFFF));
        GuiTextFieldNop x = new GuiTextFieldNop(100, (Screen) (Object) this, this.guiLeft + 190, this.guiTop + 14, 50, 20, "");
        x.setFloatsOnly().setMinMaxDefault(-20.0f, 20.0f, 0.0f);
        x.setValue(String.format("%.2f", acc.cnpcplus$getOffsetX()));
        this.addTextField(x);

        this.addLabel(new GuiLabel(111, "Y", this.guiLeft + 175, this.guiTop + 41, 0xFFFFFF));
        GuiTextFieldNop y = new GuiTextFieldNop(101, (Screen) (Object) this, this.guiLeft + 190, this.guiTop + 36, 50, 20, "");
        y.setFloatsOnly().setMinMaxDefault(-20.0f, 20.0f, 0.0f);
        y.setValue(String.format("%.2f", acc.cnpcplus$getOffsetY()));
        this.addTextField(y);

        this.addLabel(new GuiLabel(112, "Z", this.guiLeft + 175, this.guiTop + 63, 0xFFFFFF));
        GuiTextFieldNop z = new GuiTextFieldNop(102, (Screen) (Object) this, this.guiLeft + 190, this.guiTop + 58, 50, 20, "");
        z.setFloatsOnly().setMinMaxDefault(-20.0f, 20.0f, 0.0f);
        z.setValue(String.format("%.2f", acc.cnpcplus$getOffsetZ()));
        this.addTextField(z);
    }

    @Inject(method = "drawSlider", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$skipNullSlider(int y, JobPuppet.PartConfig config, CallbackInfo ci) {
        if (config == null) ci.cancel();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/util/GuiNPCInterface;drawNpc(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/LivingEntity;IIFI)V"), remap = false)
    private void cnpcplus$movePreview(GuiNPCInterface self, GuiGraphics graphics, net.minecraft.world.entity.LivingEntity entity, int x, int y, float scale, int rot) {
        this.drawNpc(graphics, entity, 420, 80, 1.5f, rot);
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        if (this.selectedName == null) return;
        JobPuppet.PartConfig config = this.data.get(this.selectedName);
        if (!(config instanceof PartConfigAccessor acc)) return;
        if (field.id == 100) {
            acc.cnpcplus$setOffsetX(cnpcplus$parseFloat(field.getValue()));
            field.setValue(String.format("%.2f", acc.cnpcplus$getOffsetX()));
        } else if (field.id == 101) {
            acc.cnpcplus$setOffsetY(cnpcplus$parseFloat(field.getValue()));
            field.setValue(String.format("%.2f", acc.cnpcplus$getOffsetY()));
        } else if (field.id == 102) {
            acc.cnpcplus$setOffsetZ(cnpcplus$parseFloat(field.getValue()));
            field.setValue(String.format("%.2f", acc.cnpcplus$getOffsetZ()));
        }
    }

    @Unique
    private static float cnpcplus$parseFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return 0.0f;
        }
    }
}
