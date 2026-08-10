package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.puppet.JobPuppetAccessor;
import bin.cnpcplus.puppet.PartConfigAccessor;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.roles.GuiNpcPuppet;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcSlider;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/**
 * Equip parts + offset fields.
 * Offset fields placed LEFT of standing/walking/attacking yes-no buttons
 * (buttons at guiLeft+110, labels at +10; fields fit between ~55-105).
 */
@Mixin(value = GuiNpcPuppet.class, remap = false)
public abstract class MixinGuiNpcPuppetEquipment implements ITextfieldListener {
    @Shadow(remap = false) private JobPuppet job;
    @Shadow(remap = false) private String selectedName;
    @Shadow(remap = false) private boolean isStart;
    @Shadow(remap = false) public HashMap data;

    @Unique
    private static final String[] EQUIP_KEYS = {
            "cnpcplus.puppet.mainhand",
            "cnpcplus.puppet.offhand",
            "cnpcplus.puppet.helmet",
            "cnpcplus.puppet.chestplate",
            "cnpcplus.puppet.leggings",
            "cnpcplus.puppet.boots"
    };

    @Inject(method = "func_73866_w_", at = @At(value = "FIELD",
            target = "Lnoppes/npcs/client/gui/roles/GuiNpcPuppet;data:Ljava/util/HashMap;",
            opcode = 181, shift = At.Shift.AFTER, ordinal = 0), remap = false)
    private void cnpcplus$injectEquipParts(CallbackInfo ci) {
        if (this.job == null || this.data == null) return;
        if (!(this.job instanceof JobPuppetAccessor)) return;
        JobPuppetAccessor acc = (JobPuppetAccessor) this.job;
        JobPuppet.PartConfig[] configs = this.isStart
                ? new JobPuppet.PartConfig[]{
                acc.cnpcplus$getMainhand(), acc.cnpcplus$getOffhand(), acc.cnpcplus$getHelmet(),
                acc.cnpcplus$getChestplate(), acc.cnpcplus$getLeggings(), acc.cnpcplus$getBoots()
        }
                : new JobPuppet.PartConfig[]{
                acc.cnpcplus$getMainhand2(), acc.cnpcplus$getOffhand2(), acc.cnpcplus$getHelmet2(),
                acc.cnpcplus$getChestplate2(), acc.cnpcplus$getLeggings2(), acc.cnpcplus$getBoots2()
        };
        for (int i = 0; i < EQUIP_KEYS.length; i++) {
            if (configs[i] != null) {
                configs[i].disabled = false;
                this.data.put(I18n.translateToLocal(EQUIP_KEYS[i]), configs[i]);
            }
        }
    }

    /**
     * Official top buttons:
     *   y = guiTop+14 standing, +22 walking, +22 attacking, +22 animation
     * Buttons at guiLeft+110. Place Off fields to the LEFT of those buttons:
     *   label at guiLeft+55, field at guiLeft+72 width 36, free space before +110.
     * Always show when a part is selected (body or equip).
     */
    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void cnpcplus$addOffsetFieldsTop(CallbackInfo ci) {
        GuiNpcPuppet self = (GuiNpcPuppet) (Object) this;
        if (this.selectedName == null || this.data == null) return;
        Object cfg = this.data.get(this.selectedName);
        if (cfg == null) return;

        float ox = 0, oy = 0, oz = 0;
        if (cfg instanceof PartConfigAccessor) {
            PartConfigAccessor acc = (PartConfigAccessor) cfg;
            ox = acc.cnpcplus$getOffsetX();
            oy = acc.cnpcplus$getOffsetY();
            oz = acc.cnpcplus$getOffsetZ();
        } else {
            // fallback reflection on public mixin fields
            try {
                ox = cfg.getClass().getField("cnpcplusOffsetX").getFloat(cfg);
                oy = cfg.getClass().getField("cnpcplusOffsetY").getFloat(cfg);
                oz = cfg.getClass().getField("cnpcplusOffsetZ").getFloat(cfg);
            } catch (Throwable ignored) {
            }
        }

        // Align with standing / walking / attacking rows (guiTop+14, +36, +58)
        int y0 = self.guiTop + 14;
        int labelX = self.guiLeft + 52;
        int fieldX = self.guiLeft + 70;
        int fieldW = 36;

        self.addLabel(new GuiNpcLabel(110, "OffX", labelX, y0 + 5, 0xFFFFFF));
        self.addTextField(new GuiNpcTextField(100, (GuiScreen) self, fieldX, y0, fieldW, 20,
                String.format("%.2f", Float.valueOf(ox))));

        int y1 = y0 + 22;
        self.addLabel(new GuiNpcLabel(111, "OffY", labelX, y1 + 5, 0xFFFFFF));
        self.addTextField(new GuiNpcTextField(101, (GuiScreen) self, fieldX, y1, fieldW, 20,
                String.format("%.2f", Float.valueOf(oy))));

        int y2 = y1 + 22;
        self.addLabel(new GuiNpcLabel(112, "OffZ", labelX, y2 + 5, 0xFFFFFF));
        self.addTextField(new GuiNpcTextField(102, (GuiScreen) self, fieldX, y2, fieldW, 20,
                String.format("%.2f", Float.valueOf(oz))));
    }

    @Inject(method = "drawSlider", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$skipNullSlider(int y, JobPuppet.PartConfig config, CallbackInfo ci) {
        if (config == null) ci.cancel();
    }

    /** Push textfield values into PartConfig every frame + live preview. */
    @Inject(method = "func_73863_a", at = @At("HEAD"), remap = false)
    private void cnpcplus$liveOffset(int mx, int my, float pt, CallbackInfo ci) {
        cnpcplus$writeOffsetToPart(false);
    }

    @Inject(method = "mouseDragged", at = @At("RETURN"), remap = false)
    private void cnpcplus$onRotDrag(GuiNpcSlider slider, CallbackInfo ci) {
        GuiNpcPuppet self = (GuiNpcPuppet) (Object) this;
        if (self.npc != null) self.npc.updateClient = true;
    }

    @Override
    public void unFocused(GuiNpcTextField field) {
        cnpcplus$writeOffsetToPart(true);
    }

    @Unique
    private void cnpcplus$writeOffsetToPart(boolean format) {
        if (this.selectedName == null || this.data == null) return;
        Object cfg = this.data.get(this.selectedName);
        if (cfg == null) return;
        GuiNpcPuppet self = (GuiNpcPuppet) (Object) this;
        try {
            GuiNpcTextField fx = self.getTextField(100);
            GuiNpcTextField fy = self.getTextField(101);
            GuiNpcTextField fz = self.getTextField(102);
            if (fx == null && fy == null && fz == null) return;

            float vx = fx != null ? parse(fx.getText()) : 0;
            float vy = fy != null ? parse(fy.getText()) : 0;
            float vz = fz != null ? parse(fz.getText()) : 0;

            if (cfg instanceof PartConfigAccessor) {
                PartConfigAccessor acc = (PartConfigAccessor) cfg;
                if (fx != null) acc.cnpcplus$setOffsetX(vx);
                if (fy != null) acc.cnpcplus$setOffsetY(vy);
                if (fz != null) acc.cnpcplus$setOffsetZ(vz);
                if (format) {
                    if (fx != null && !fx.isFocused()) {
                        fx.setText(String.format("%.2f", Float.valueOf(acc.cnpcplus$getOffsetX())));
                    }
                    if (fy != null && !fy.isFocused()) {
                        fy.setText(String.format("%.2f", Float.valueOf(acc.cnpcplus$getOffsetY())));
                    }
                    if (fz != null && !fz.isFocused()) {
                        fz.setText(String.format("%.2f", Float.valueOf(acc.cnpcplus$getOffsetZ())));
                    }
                }
            } else {
                // direct field write
                if (fx != null) cfg.getClass().getField("cnpcplusOffsetX").setFloat(cfg, clamp(vx));
                if (fy != null) cfg.getClass().getField("cnpcplusOffsetY").setFloat(cfg, clamp(vy));
                if (fz != null) cfg.getClass().getField("cnpcplusOffsetZ").setFloat(cfg, clamp(vz));
            }
            if (self.npc != null) {
                self.npc.updateClient = true;
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    @Unique
    private static float parse(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return 0.0f;
        }
    }

    @Unique
    private static float clamp(float v) {
        if (v < -20.0f) return -20.0f;
        if (v > 20.0f) return 20.0f;
        return v;
    }
}
