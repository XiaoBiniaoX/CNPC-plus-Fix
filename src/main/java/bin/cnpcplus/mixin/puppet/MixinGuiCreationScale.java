package bin.cnpcplus.mixin.puppet;

import net.minecraft.client.resources.language.I18n;
import noppes.npcs.ModelPartConfig;
import noppes.npcs.client.gui.model.GuiCreationScale;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.constants.EnumParts;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiSliderNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.accessor.EquipmentModelDataAccessor;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = GuiCreationScale.class, remap = false)
public abstract class MixinGuiCreationScale implements ITextfieldListener {

    @Shadow(remap = false)
    private GuiCustomScrollNop scroll;
    @Shadow(remap = false)
    private List<EnumParts> data;
    @Shadow(remap = false)
    private static EnumParts selected;

    @Unique
    private static final String[] CNPCPLUS_EQUIP_PARTS = {"mainhand", "offhand", "helmet", "chestplate", "leggings", "boots"};
    @Unique
    private static int cnpcplus$equipSelected = -1;
    @Unique
    private boolean cnpcplus$initFinished = false;
    @Unique
    private float cnpcplus$savedHeadX, cnpcplus$savedHeadY, cnpcplus$savedHeadZ;

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiCustomScrollNop;setUnsortedList(Ljava/util/List;)V"))
    private List<String> cnpcplus$addEquipNames(List<String> list) {
        List<String> result = new ArrayList<>(list);
        for (String equip : CNPCPLUS_EQUIP_PARTS) {
            result.add(I18n.get("cnpcplus.equip." + equip));
        }
        return result;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void cnpcplus$onInitHead(CallbackInfo ci) {
        cnpcplus$initFinished = false;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;
        ModelPartConfig head = screen.playerdata.getPartConfig(EnumParts.HEAD);
        cnpcplus$savedHeadX = head.scaleX;
        cnpcplus$savedHeadY = head.scaleY;
        cnpcplus$savedHeadZ = head.scaleZ;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$onInitTail(CallbackInfo ci) {
        cnpcplus$initFinished = false;
        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;

        if (cnpcplus$equipSelected >= 0) {
            self.wrapper.labels.remove(10);
            self.wrapper.labels.remove(11);
            self.wrapper.labels.remove(12);
            self.wrapper.labels.remove(13);
            GuiSliderNop s10 = self.wrapper.sliders.remove(10);
            if (s10 != null) s10.visible = false;
            GuiSliderNop s11 = self.wrapper.sliders.remove(11);
            if (s11 != null) s11.visible = false;
            GuiSliderNop s12 = self.wrapper.sliders.remove(12);
            if (s12 != null) s12.visible = false;

            ModelPartConfig config = cnpcplus$getEquipConfig(CNPCPLUS_EQUIP_PARTS[cnpcplus$equipSelected]);
            if (config == null) { cnpcplus$initFinished = true; return; }

            int y = self.guiTop + 65;

            self.addLabel(new GuiLabel(100, "cnpcplus.scale.width", self.guiLeft + 102, y + 5, 0xFFFFFF));
            GuiSliderNop sw = new GuiSliderNop(self, 10, self.guiLeft + 150, y, 100, 20, 0);
            self.addSlider(sw);
            GuiTextFieldNop tfW = new GuiTextFieldNop(100, self, self.guiLeft + 255, y, 55, 20, "");
            tfW.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfW.setValue(String.format("%.2f", config.scaleX));
            self.addTextField(tfW);

            self.addLabel(new GuiLabel(101, "cnpcplus.scale.height", self.guiLeft + 102, (y += 22) + 5, 0xFFFFFF));
            GuiSliderNop sh = new GuiSliderNop(self, 11, self.guiLeft + 150, y, 100, 20, 0);
            self.addSlider(sh);
            GuiTextFieldNop tfH = new GuiTextFieldNop(101, self, self.guiLeft + 255, y, 55, 20, "");
            tfH.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfH.setValue(String.format("%.2f", config.scaleY));
            self.addTextField(tfH);

            self.addLabel(new GuiLabel(102, "cnpcplus.scale.depth", self.guiLeft + 102, (y += 22) + 5, 0xFFFFFF));
            GuiSliderNop sd = new GuiSliderNop(self, 12, self.guiLeft + 150, y, 100, 20, 0);
            self.addSlider(sd);
            GuiTextFieldNop tfD = new GuiTextFieldNop(102, self, self.guiLeft + 255, y, 55, 20, "");
            tfD.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfD.setValue(String.format("%.2f", config.scaleZ));
            self.addTextField(tfD);

            cnpcplus$fixSlider(sw, config.scaleX);
            cnpcplus$fixSlider(sh, config.scaleY);
            cnpcplus$fixSlider(sd, config.scaleZ);

            this.scroll.setSelected(I18n.get("cnpcplus.equip." + CNPCPLUS_EQUIP_PARTS[cnpcplus$equipSelected]));
        } else {
            ModelPartConfig config = screen.playerdata.getPartConfig(selected);
            float sx = config.scaleX;
            float sy = config.scaleY;
            float sz = config.scaleZ;

            GuiSliderNop sw = self.wrapper.sliders.get(10);
            if (sw != null) cnpcplus$fixSlider(sw, sx);
            GuiSliderNop sh = self.wrapper.sliders.get(11);
            if (sh != null) cnpcplus$fixSlider(sh, sy);
            GuiSliderNop sd = self.wrapper.sliders.get(12);
            if (sd != null) cnpcplus$fixSlider(sd, sz);

            int y = self.guiTop + 65;
            GuiCreationScale cs = (GuiCreationScale)(Object)this;

            GuiTextFieldNop tfW = new GuiTextFieldNop(100, cs, cs.guiLeft + 255, y, 55, 20, "");
            tfW.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfW.setValue(String.format("%.2f", sx));
            cs.addTextField(tfW);

            GuiTextFieldNop tfH = new GuiTextFieldNop(101, cs, cs.guiLeft + 255, (y += 22), 55, 20, "");
            tfH.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfH.setValue(String.format("%.2f", sy));
            cs.addTextField(tfH);

            GuiTextFieldNop tfD = new GuiTextFieldNop(102, cs, cs.guiLeft + 255, (y += 22), 55, 20, "");
            tfD.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfD.setValue(String.format("%.2f", sz));
            cs.addTextField(tfD);
        }

        ModelPartConfig head = screen.playerdata.getPartConfig(EnumParts.HEAD);
        head.scaleX = cnpcplus$savedHeadX;
        head.scaleY = cnpcplus$savedHeadY;
        head.scaleZ = cnpcplus$savedHeadZ;

        cnpcplus$initFinished = true;
    }

    @Override
    public void unFocused(GuiTextFieldNop tf) {
        if (tf.id < 100 || tf.id > 102) return;
        float v = cnpcplus$clamp(tf.getValue(), 0.0f, 10.0f);
        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;

        if (cnpcplus$equipSelected >= 0) {
            ModelPartConfig config = cnpcplus$getEquipConfig(CNPCPLUS_EQUIP_PARTS[cnpcplus$equipSelected]);
            if (config == null) return;
            if (tf.id == 100) { config.scaleX = v; }
            else if (tf.id == 101) { config.scaleY = v; }
            else { config.scaleZ = v; }
            tf.setValue(String.format("%.2f", v));
            GuiSliderNop slider = self.wrapper.sliders.get(tf.id - 90);
            if (slider != null) cnpcplus$fixSlider(slider, v);
        } else {
            ModelPartConfig config = screen.playerdata.getPartConfig(selected);
            if (config == null) return;
            if (tf.id == 100) { config.scaleX = v; }
            else if (tf.id == 101) { config.scaleY = v; }
            else { config.scaleZ = v; }
            tf.setValue(String.format("%.2f", v));
            GuiSliderNop slider = self.wrapper.sliders.get(tf.id - 90);
            if (slider != null) cnpcplus$fixSlider(slider, v);
        }
    }

    @Inject(method = "scrollClicked", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onScrollClicked(double i, double j, int k, GuiCustomScrollNop scroll, CallbackInfo ci) {
        if (!scroll.hasSelected()) return;
        int idx = scroll.getSelectedIndex();
        if (idx >= this.data.size()) {
            cnpcplus$equipSelected = idx - this.data.size();
            selected = EnumParts.HEAD;
            ((GuiCreationScale) (Object) this).init();
            ci.cancel();
        } else {
            cnpcplus$equipSelected = -1;
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onMouseDragged(GuiSliderNop slider, CallbackInfo ci) {
        if (slider.id < 10 || slider.id > 12) return;

        if (!cnpcplus$initFinished) {
            if (cnpcplus$equipSelected >= 0) {
                ci.cancel();
            }
            return;
        }

        ci.cancel();
        int percent = (int)(slider.sliderValue * 1000.0f);
        slider.setString(percent + "%");
        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;

        if (cnpcplus$equipSelected >= 0) {
            ModelPartConfig config = cnpcplus$getEquipConfig(CNPCPLUS_EQUIP_PARTS[cnpcplus$equipSelected]);
            if (config == null) return;
            float v = slider.sliderValue * 10.0f;
            if (slider.id == 10) config.scaleX = v;
            else if (slider.id == 11) config.scaleY = v;
            else config.scaleZ = v;
            GuiTextFieldNop tf = self.wrapper.textfields.get(slider.id + 90);
            if (tf != null) tf.setValue(String.format("%.2f", v));
        } else {
            ModelPartConfig config = screen.playerdata.getPartConfig(selected);
            if (config == null) return;
            float v = slider.sliderValue * 10.0f;
            if (slider.id == 10) config.scaleX = v;
            else if (slider.id == 11) config.scaleY = v;
            else config.scaleZ = v;
            GuiTextFieldNop tf = self.wrapper.textfields.get(slider.id + 90);
            if (tf != null) tf.setValue(String.format("%.2f", v));
        }
    }

    @Unique
    private static void cnpcplus$fixSlider(GuiSliderNop slider, float scale) {
        slider.sliderValue = scale / 10.0f;
        int pct = (int)(slider.sliderValue * 1000.0f);
        slider.setString(pct + "%");
    }

    @Unique
    private ModelPartConfig cnpcplus$getEquipConfig(String name) {
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;
        EquipmentModelDataAccessor accessor = (EquipmentModelDataAccessor) screen.playerdata;
        switch (name) {
            case "mainhand": return accessor.getMainhand();
            case "offhand": return accessor.getOffhand();
            case "helmet": return accessor.getHelmet();
            case "chestplate": return accessor.getChestplate();
            case "leggings": return accessor.getLeggings();
            case "boots": return accessor.getBoots();
            default: return null;
        }
    }

    @Unique
    private static float cnpcplus$clamp(String s, float min, float max) {
        try {
            float v = Float.parseFloat(s);
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return 1.0f;
        }
    }
}