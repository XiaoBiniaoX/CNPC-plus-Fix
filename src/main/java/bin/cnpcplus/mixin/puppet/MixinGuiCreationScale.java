package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.accessor.EquipmentModelDataAccessor;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.ModelPartConfig;
import noppes.npcs.client.gui.model.GuiCreationScale;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcSlider;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.constants.EnumParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of 1.21 equip scale editor:
 * - body part sliders use 0-10 range (value = slider*10)
 * - extra 6 equip entries in scroll
 * - text fields for precise values
 */
@Mixin(value = GuiCreationScale.class, remap = false)
public abstract class MixinGuiCreationScale implements ITextfieldListener {

    @Shadow(remap = false) private GuiCustomScroll scroll;
    @Shadow(remap = false) private List data;

    @Unique private static final String[] CNPCPLUS_EQUIP = {"mainhand", "offhand", "helmet", "chestplate", "leggings", "boots"};
    @Unique private static int cnpcplus$equipSelected = -1;
    @Unique private boolean cnpcplus$initDone;

    @Inject(method = "func_73866_w_", at = @At("HEAD"), remap = false)
    private void cnpcplus$onInitHead(CallbackInfo ci) {
        cnpcplus$initDone = false;
    }

    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void cnpcplus$onInit(CallbackInfo ci) {
        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;

        if (this.scroll != null && this.data != null) {
            try {
                ArrayList list = new ArrayList();
                for (int i = 0; i < this.data.size(); i++) {
                    EnumParts part = (EnumParts) this.data.get(i);
                    list.add(I18n.translateToLocal("part." + part.name));
                }
                for (int i = 0; i < CNPCPLUS_EQUIP.length; i++) {
                    list.add(I18n.translateToLocal("cnpcplus.equip." + CNPCPLUS_EQUIP[i]));
                }
                this.scroll.setUnsortedList(list);
                if (cnpcplus$equipSelected >= 0 && cnpcplus$equipSelected < CNPCPLUS_EQUIP.length) {
                    this.scroll.setSelected(I18n.translateToLocal("cnpcplus.equip." + CNPCPLUS_EQUIP[cnpcplus$equipSelected]));
                }
            } catch (Throwable ignored) {
            }
        }

        ModelPartConfig config;
        if (cnpcplus$equipSelected >= 0 && cnpcplus$equipSelected < CNPCPLUS_EQUIP.length) {
            config = cnpcplus$getEquipConfig(screen, CNPCPLUS_EQUIP[cnpcplus$equipSelected]);
            // hide shared button for equip
            try {
                if (self.getButton(13) != null) self.getButton(13).setVisible(false);
                if (self.getLabel(13) != null) { /* keep */ }
            } catch (Throwable ignored) {
            }
        } else {
            // body part: retarget existing sliders to 0-10 mapping
            config = screen.playerdata != null ? screen.playerdata.getPartConfig(cnpcplus$selected()) : null;
        }
        if (config == null) {
            cnpcplus$initDone = true;
            return;
        }

        // Fix slider values to 0-10 range (official uses scale-0.5 for 0.5-1.5)
        try {
            GuiNpcSlider s10 = self.getSlider(10);
            GuiNpcSlider s11 = self.getSlider(11);
            GuiNpcSlider s12 = self.getSlider(12);
            if (s10 != null) cnpcplus$fixSlider(s10, config.scaleX);
            if (s11 != null) cnpcplus$fixSlider(s11, config.scaleY);
            if (s12 != null) cnpcplus$fixSlider(s12, config.scaleZ);
        } catch (Throwable ignored) {
        }

        // If equip selected, official built body sliders for HEAD - rebuild for equip config already done via fixSlider
        // Add numeric text fields
        int y = self.guiTop + 65;
        if (self.getTextField(100) == null) {
            self.addTextField(new GuiNpcTextField(100, self, self.guiLeft + 255, y, 55, 20, String.format("%.2f", Float.valueOf(config.scaleX))));
        } else {
            self.getTextField(100).setText(String.format("%.2f", Float.valueOf(config.scaleX)));
        }
        y += 22;
        if (self.getTextField(101) == null) {
            self.addTextField(new GuiNpcTextField(101, self, self.guiLeft + 255, y, 55, 20, String.format("%.2f", Float.valueOf(config.scaleY))));
        } else {
            self.getTextField(101).setText(String.format("%.2f", Float.valueOf(config.scaleY)));
        }
        y += 22;
        if (self.getTextField(102) == null) {
            self.addTextField(new GuiNpcTextField(102, self, self.guiLeft + 255, y, 55, 20, String.format("%.2f", Float.valueOf(config.scaleZ))));
        } else {
            self.getTextField(102).setText(String.format("%.2f", Float.valueOf(config.scaleZ)));
        }

        // When equip selected, also force label keys
        if (cnpcplus$equipSelected >= 0) {
            try {
                // recreate labels with equip-aware keys is optional; existing scale.width etc ok
            } catch (Throwable ignored) {
            }
        }

        cnpcplus$initDone = true;
    }

    @Inject(method = "scrollClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$onScroll(int i, int j, int k, GuiCustomScroll scroll, CallbackInfo ci) {
        if (scroll == null || this.data == null) return;
        int idx = scroll.selected;
        if (idx < 0) return;
        if (idx >= this.data.size()) {
            cnpcplus$equipSelected = idx - this.data.size();
            if (cnpcplus$equipSelected < 0 || cnpcplus$equipSelected >= CNPCPLUS_EQUIP.length) {
                cnpcplus$equipSelected = -1;
            }
            ((GuiCreationScale) (Object) this).func_73866_w_();
            ci.cancel();
        } else {
            cnpcplus$equipSelected = -1;
            // let official set selected EnumParts
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$onDrag(GuiNpcSlider slider, CallbackInfo ci) {
        int sid = slider != null ? slider.id : -1;
        if (!cnpcplus$initDone) {
            if (sid >= 10 && sid <= 12) {
                ci.cancel();
            }
            return;
        }
        if (sid < 10 || sid > 12) return;

        float v = slider.sliderValue * 10.0f;
        if (v < 0.0f) v = 0.0f;
        if (v > 10.0f) v = 10.0f;
        int percent = (int) (v * 100.0f);
        slider.setString(percent + "%");

        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;
        ModelPartConfig config;
        if (cnpcplus$equipSelected >= 0) {
            config = cnpcplus$getEquipConfig(screen, CNPCPLUS_EQUIP[cnpcplus$equipSelected]);
        } else {
            config = screen.playerdata != null ? screen.playerdata.getPartConfig(cnpcplus$selected()) : null;
        }
        if (config == null) {
            ci.cancel();
            return;
        }
        // write raw fields (bypass setScale clamp)
        if (sid == 10) config.scaleX = v;
        if (sid == 11) config.scaleY = v;
        if (sid == 12) config.scaleZ = v;

        GuiNpcTextField tf = self.getTextField(sid + 90);
        if (tf != null) {
            tf.setText(String.format("%.2f", Float.valueOf(v)));
        }
        // skip official 0.5-1.5 mapping (scale = slider+0.5)
        ci.cancel();
    }

    @Override
    public void unFocused(GuiNpcTextField tf) {
        int fid = tf.getId();
        if (fid < 100 || fid > 102) return;
        float v;
        try {
            v = Float.parseFloat(tf.getText());
        } catch (Exception e) {
            v = 1.0f;
        }
        if (v < 0.0f) v = 0.0f;
        if (v > 10.0f) v = 10.0f;

        GuiCreationScale self = (GuiCreationScale) (Object) this;
        GuiCreationScreenInterface screen = (GuiCreationScreenInterface) (Object) this;
        ModelPartConfig config;
        if (cnpcplus$equipSelected >= 0) {
            config = cnpcplus$getEquipConfig(screen, CNPCPLUS_EQUIP[cnpcplus$equipSelected]);
        } else {
            config = screen.playerdata != null ? screen.playerdata.getPartConfig(cnpcplus$selected()) : null;
        }
        if (config == null) return;
        if (fid == 100) config.scaleX = v;
        else if (fid == 101) config.scaleY = v;
        else config.scaleZ = v;
        tf.setText(String.format("%.2f", Float.valueOf(v)));

        GuiNpcSlider slider = self.getSlider(fid - 90);
        if (slider != null) cnpcplus$fixSlider(slider, v);
    }

    @Unique
    private static void cnpcplus$fixSlider(GuiNpcSlider slider, float scale) {
        float sv = scale / 10.0f;
        if (sv < 0.0f) sv = 0.0f;
        if (sv > 1.0f) sv = 1.0f;
        slider.sliderValue = sv;
        slider.setString(((int) (scale * 100.0f)) + "%");
    }

    @Unique
    private EnumParts cnpcplus$selected() {
        try {
            java.lang.reflect.Field f = GuiCreationScale.class.getDeclaredField("selected");
            f.setAccessible(true);
            return (EnumParts) f.get(null);
        } catch (Throwable t) {
            return EnumParts.HEAD;
        }
    }

    @Unique
    private ModelPartConfig cnpcplus$getEquipConfig(GuiCreationScreenInterface screen, String name) {
        if (screen.playerdata == null) return null;
        if (!(screen.playerdata instanceof EquipmentModelDataAccessor)) return null;
        EquipmentModelDataAccessor acc = (EquipmentModelDataAccessor) screen.playerdata;
        if ("mainhand".equals(name)) return acc.getMainhand();
        if ("offhand".equals(name)) return acc.getOffhand();
        if ("helmet".equals(name)) return acc.getHelmet();
        if ("chestplate".equals(name)) return acc.getChestplate();
        if ("leggings".equals(name)) return acc.getLeggings();
        if ("boots".equals(name)) return acc.getBoots();
        return null;
    }
}
