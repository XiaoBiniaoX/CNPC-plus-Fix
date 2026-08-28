package bin.cnpcplus.mixin.stats;

import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.entity.data.DataStats;
import bin.cnpcplus.common.IDataStatsFloatAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcStats.class, remap = false)
public abstract class MixinGuiNpcStatsFloat {

    @Shadow(remap = false)
    private DataStats stats;

    @Inject(method = "func_73866_w_", at = @At("TAIL"))
    private void cnpcplus$onInitGui(CallbackInfo ci) {
        GuiNpcStats self = (GuiNpcStats) (Object) this;
        IDataStatsFloatAccess acc = (IDataStatsFloatAccess) this.stats;
        GuiNpcTextField healthField = self.getTextField(0);
        if (healthField != null) {
            healthField.numbersOnly = false;
            healthField.setText(String.valueOf(acc.cnpcplus$getMaxHealthFloat()));
        }
        GuiNpcTextField regenField = self.getTextField(14);
        if (regenField != null) {
            regenField.numbersOnly = false;
            regenField.setText(String.valueOf(acc.cnpcplus$getHealthRegenFloat()));
        }
        GuiNpcTextField combatRegenField = self.getTextField(16);
        if (combatRegenField != null) {
            combatRegenField.numbersOnly = false;
            combatRegenField.setText(String.valueOf(acc.cnpcplus$getCombatRegenFloat()));
        }
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onUnfocused(GuiNpcTextField textfield, CallbackInfo ci) {
        GuiNpcStats self = (GuiNpcStats) (Object) this;
        IDataStatsFloatAccess acc = (IDataStatsFloatAccess) this.stats;
        if (self.getTextField(0) == textfield) {
            String text = textfield.getText().trim();
            float val = 20.0f;
            if (!text.isEmpty()) {
                try {
                    val = Float.parseFloat(text);
                } catch (NumberFormatException e) {
                    val = 20.0f;
                }
            }
            if (val <= 0.0f) {
                val = 20.0f;
            }
            acc.cnpcplus$setMaxHealthFloat(val);
            ci.cancel();
            return;
        }
        if (self.getTextField(14) == textfield) {
            String text = textfield.getText().trim();
            float val = 0.0f;
            if (!text.isEmpty()) {
                try {
                    val = Float.parseFloat(text);
                } catch (NumberFormatException e) {
                    val = 0.0f;
                }
            }
            if (val < 0.0f) {
                val = 0.0f;
            }
            acc.cnpcplus$setHealthRegenFloat(val);
            ci.cancel();
            return;
        }
        if (self.getTextField(16) == textfield) {
            String text = textfield.getText().trim();
            float val = 0.0f;
            if (!text.isEmpty()) {
                try {
                    val = Float.parseFloat(text);
                } catch (NumberFormatException e) {
                    val = 0.0f;
                }
            }
            if (val < 0.0f) {
                val = 0.0f;
            }
            acc.cnpcplus$setCombatRegenFloat(val);
            ci.cancel();
        }
    }
}