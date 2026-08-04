package top.cnpcplus.mixin;

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
import top.cnpcplus.accessor.EquipmentModelDataAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    private static final String[] EQUIP_PARTS = {"mainhand", "offhand", "helmet", "chestplate", "leggings", "boots"};
    @Unique
    private static int equipSelected = -1;

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiSliderNop;<init>(Lnet/minecraft/client/gui/screens/Screen;IIIIIF)V"), index = 6)
    private float fixSliderVal(float val) {
        return (val + 0.5f) / 10.0f;
    }

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiCustomScrollNop;setUnsortedList(Ljava/util/List;)V"))
    private List<String> addEquipNames(List<String> list) {
        List<String> result = new ArrayList<>(list);
        for (String equip : EQUIP_PARTS) {
            result.add(I18n.get("cnpcplus.equip." + equip));
        }
        return result;
    }

    @Inject(method = "m_7856_", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;

        if (equipSelected >= 0) {
            // remove body-part labels & sliders (IDs 10-13)
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

            ModelPartConfig config = getEquipConfig(EQUIP_PARTS[equipSelected]);
            if (config == null) return;

            int y = self.guiTop + 65;

            self.addLabel(new GuiLabel(100, "cnpcplus.scale.width", self.guiLeft + 102, y + 5, 0xFFFFFF));
            // @ModifyArg fixSliderVal only applies to original method bytecode, NOT onInitTail injected code
            GuiSliderNop sw = new GuiSliderNop(self, 10, self.guiLeft + 150, y, 100, 20, config.scaleX / 10.0f);
            self.addSlider(sw);
            GuiTextFieldNop tfW = new GuiTextFieldNop(100, self, self.guiLeft + 255, y, 55, 20, "");
            tfW.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfW.setValue(String.format("%.2f", config.scaleX));
            self.addTextField(tfW);

            self.addLabel(new GuiLabel(101, "cnpcplus.scale.height", self.guiLeft + 102, (y += 22) + 5, 0xFFFFFF));
            GuiSliderNop sh = new GuiSliderNop(self, 11, self.guiLeft + 150, y, 100, 20, config.scaleY / 10.0f);
            self.addSlider(sh);
            GuiTextFieldNop tfH = new GuiTextFieldNop(101, self, self.guiLeft + 255, y, 55, 20, "");
            tfH.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfH.setValue(String.format("%.2f", config.scaleY));
            self.addTextField(tfH);

            self.addLabel(new GuiLabel(102, "cnpcplus.scale.depth", self.guiLeft + 102, (y += 22) + 5, 0xFFFFFF));
            GuiSliderNop sd = new GuiSliderNop(self, 12, self.guiLeft + 150, y, 100, 20, config.scaleZ / 10.0f);
            self.addSlider(sd);
            GuiTextFieldNop tfD = new GuiTextFieldNop(102, self, self.guiLeft + 255, y, 55, 20, "");
            tfD.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfD.setValue(String.format("%.2f", config.scaleZ));
            self.addTextField(tfD);
            scroll.setSelected(I18n.get("cnpcplus.equip." + EQUIP_PARTS[equipSelected]));
        } else {
            // add text-fields next to the existing body-part sliders
            GuiCreationScale cs = (GuiCreationScale)(Object)this;
            int y = cs.guiTop + 65;

            GuiTextFieldNop tfW = new GuiTextFieldNop(100, cs, cs.guiLeft + 255, y, 55, 20, "");
            tfW.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfW.setValue(String.format("%.2f", screen.playerdata.getPartConfig(EnumParts.HEAD).scaleX));
            cs.addTextField(tfW);

            GuiTextFieldNop tfH = new GuiTextFieldNop(101, cs, cs.guiLeft + 255, (y += 22), 55, 20, "");
            tfH.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfH.setValue(String.format("%.2f", screen.playerdata.getPartConfig(EnumParts.HEAD).scaleY));
            cs.addTextField(tfH);

            GuiTextFieldNop tfD = new GuiTextFieldNop(102, cs, cs.guiLeft + 255, (y += 22), 55, 20, "");
            tfD.setFloatsOnly().setMinMaxDefault(0.0f, 10.0f, 1.0f);
            tfD.setValue(String.format("%.2f", screen.playerdata.getPartConfig(EnumParts.HEAD).scaleZ));
            cs.addTextField(tfD);
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop tf) {
        if (tf.id < 100 || tf.id > 102) return;
        float v = clamp(tf.getValue(), 0.0f, 10.0f);
        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;

        if (equipSelected >= 0) {
            ModelPartConfig config = getEquipConfig(EQUIP_PARTS[equipSelected]);
            if (config == null) return;
            float oldV = v;
            if (tf.id == 100) { config.scaleX = v; }
            else if (tf.id == 101) { config.scaleY = v; }
            else { config.scaleZ = v; }
            tf.setValue(String.format("%.2f", v));
            // sync slider
            GuiSliderNop slider = self.wrapper.sliders.get(tf.id - 90);
            if (slider != null) slider.sliderValue = v / 10.0f;
        } else {
            ModelPartConfig config = screen.playerdata.getPartConfig(selected);
            if (config == null) return;
            if (tf.id == 100) { config.scaleX = v; }
            else if (tf.id == 101) { config.scaleY = v; }
            else { config.scaleZ = v; }
            tf.setValue(String.format("%.2f", v));
            GuiSliderNop slider = self.wrapper.sliders.get(tf.id - 90);
            if (slider != null) slider.sliderValue = v / 10.0f;
            // updateTransate is private, will be refreshed on next scroll click
        }
    }

    @Inject(method = "scrollClicked", at = @At("HEAD"), cancellable = true)
    private void onScrollClicked(double i, double j, int k, GuiCustomScrollNop scroll, CallbackInfo ci) {
        if (!scroll.hasSelected()) return;
        int idx = scroll.getSelectedIndex();
        if (idx >= this.data.size()) {
            equipSelected = idx - this.data.size();
            this.selected = EnumParts.HEAD;
            ((GuiCreationScale) (Object) this).m_7856_();
            ci.cancel();
        } else {
            equipSelected = -1;
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(GuiSliderNop slider, CallbackInfo ci) {
        if (slider.id < 10 || slider.id > 12) return;
        int percent = (int)(slider.sliderValue * 1000.0f);
        slider.setString(percent + "%");
        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;

        if (equipSelected >= 0) {
            ModelPartConfig config = getEquipConfig(EQUIP_PARTS[equipSelected]);
            if (config == null) return;
            float v = slider.sliderValue * 10.0f;
            if (slider.id == 10) config.scaleX = v;
            else if (slider.id == 11) config.scaleY = v;
            else config.scaleZ = v;
            // sync text field
            GuiTextFieldNop tf = self.wrapper.textfields.get(slider.id + 90);
            if (tf != null) tf.setValue(String.format("%.2f", v));
            ci.cancel();
        } else {
            ModelPartConfig config = screen.playerdata.getPartConfig(selected);
            if (config == null) return;
            float v = slider.sliderValue * 10.0f;
            if (slider.id == 10) config.scaleX = v;
            else if (slider.id == 11) config.scaleY = v;
            else config.scaleZ = v;
            // sync text field
            GuiTextFieldNop tf = self.wrapper.textfields.get(slider.id + 90);
            if (tf != null) tf.setValue(String.format("%.2f", v));
            // updateTransate is private in GuiCreationScale, skip it
            ci.cancel();
        }
    }

    @Unique
    private ModelPartConfig getEquipConfig(String name) {
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
    private static float clamp(String s, float min, float max) {
        try {
            float v = Float.parseFloat(s);
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return 1.0f;
        }
    }
}
