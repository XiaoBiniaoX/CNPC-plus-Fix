package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.accessor.ResistExtraAccess;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.Resistances;
import noppes.npcs.client.gui.SubGuiNpcResistanceProperties;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiSliderNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcResistanceProperties.class, remap = false)
public class MixinSubGuiNpcResistanceExtra {

    @Shadow
    private Resistances resistances;

    @Unique
    private GuiSliderNop cnpcplus$genericSlider;
    @Unique
    private GuiSliderNop cnpcplus$magicSlider;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$initExtraSliders(CallbackInfo ci) {
        SubGuiNpcResistanceProperties self = (SubGuiNpcResistanceProperties)(Object)this;
        ResistExtraAccess access = (ResistExtraAccess) resistances;

        // 通用伤害滑块（generic, cramming, anvil, cactus）
        self.addLabel(new GuiLabel(10, "cnpcplus.genericdamage", self.guiLeft + 4, self.guiTop + 103, ""));
        this.cnpcplus$genericSlider = new GuiSliderNop((Screen)self, 10, self.guiLeft + 94, self.guiTop + 98,
                (int)(access.cnpcplus$getGenericDamage() * 100.0f - 100.0f) + "%",
                access.cnpcplus$getGenericDamage() / 2.0f);
        self.addSlider(this.cnpcplus$genericSlider);

        // 魔法伤害滑块（indirectMagic, sonicBoom, thorns, magic, lightningBolt, genericKill, outOfBorder）
        self.addLabel(new GuiLabel(11, "cnpcplus.magicdamage", self.guiLeft + 4, self.guiTop + 125, ""));
        this.cnpcplus$magicSlider = new GuiSliderNop((Screen)self, 11, self.guiLeft + 94, self.guiTop + 120,
                (int)(access.cnpcplus$getMagicDamage() * 100.0f - 100.0f) + "%",
                access.cnpcplus$getMagicDamage() / 2.0f);
        self.addSlider(this.cnpcplus$magicSlider);
    }

    @Inject(method = "mouseDragged", at = @At("TAIL"))
    private void cnpcplus$mouseDragged(GuiSliderNop slider, CallbackInfo ci) {
        if (slider.id == 10) {
            slider.setString((int)(slider.sliderValue * 200.0f - 100.0f) + "%");
        } else if (slider.id == 11) {
            slider.setString((int)(slider.sliderValue * 200.0f - 100.0f) + "%");
        }
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"))
    private void cnpcplus$mouseReleased(GuiSliderNop slider, CallbackInfo ci) {
        ResistExtraAccess access = (ResistExtraAccess) resistances;
        if (slider.id == 10) {
            access.cnpcplus$setGenericDamage(slider.sliderValue * 2.0f);
        } else if (slider.id == 11) {
            access.cnpcplus$setMagicDamage(slider.sliderValue * 2.0f);
        }
    }
}