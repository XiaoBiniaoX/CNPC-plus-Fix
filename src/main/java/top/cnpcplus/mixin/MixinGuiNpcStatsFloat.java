package top.cnpcplus.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataStats;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = GuiNpcStats.class, remap = false)
public class MixinGuiNpcStatsFloat {

    @Shadow(remap = false)
    private DataStats stats;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$convertFieldsToFloat(CallbackInfo ci) {
        GuiNpcStats self = (GuiNpcStats) (Object) this;
        GuiTextFieldNop healthField = self.getTextField(0);
        if (healthField != null) {
            healthField.numbersOnly = false;
            healthField.floatsOnly = true;
            healthField.setMinMaxDefault(0.0f, Float.MAX_VALUE, 20.0f);
            float v = ExtraDataStorage.getFloat(stats, 0);
            healthField.setValue(String.valueOf(v < 0.0f ? (float) stats.maxHealth : v));
        }
        GuiTextFieldNop regenField = self.getTextField(14);
        if (regenField != null) {
            regenField.numbersOnly = false;
            regenField.floatsOnly = true;
            regenField.setMinMaxDefault(0.0f, Float.MAX_VALUE, 1.0f);
            float v = ExtraDataStorage.getFloat(stats, 1);
            regenField.setValue(String.valueOf(v < 0.0f ? (float) stats.healthRegen : v));
        }
        GuiTextFieldNop combatField = self.getTextField(16);
        if (combatField != null) {
            combatField.numbersOnly = false;
            combatField.floatsOnly = true;
            combatField.setMinMaxDefault(0.0f, Float.MAX_VALUE, 0.0f);
            float v = ExtraDataStorage.getFloat(stats, 2);
            combatField.setValue(String.valueOf(v < 0.0f ? (float) stats.combatRegen : v));
        }
    }

    @Inject(method = "unFocused", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleFloatUnfocused(GuiTextFieldNop textfield, CallbackInfo ci) {
        GuiNpcStats self = (GuiNpcStats) (Object) this;
        if (textfield.id == 0) {
            float val;
            if (textfield.getValue() == null || textfield.getValue().trim().isEmpty()) {
                val = 20.0f;
            } else {
                try { val = Float.parseFloat(textfield.getValue().trim()); } catch (NumberFormatException e) { val = 20.0f; }
            }
            if (val <= 0.0f) val = 20.0f;
            ExtraDataStorage.setFloat(stats, 0, val);
            stats.maxHealth = Math.round(val);
            self.npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue((double) val);
            self.npc.updateClient = true;
            self.npc.heal(val);
            ci.cancel();
        } else if (textfield.id == 14) {
            float val;
            if (textfield.getValue() == null || textfield.getValue().trim().isEmpty()) {
                val = 1.0f;
            } else {
                try { val = Float.parseFloat(textfield.getValue().trim()); } catch (NumberFormatException e) { val = 1.0f; }
            }
            ExtraDataStorage.setFloat(stats, 1, val);
            stats.healthRegen = Math.round(val);
            ci.cancel();
        } else if (textfield.id == 16) {
            float val;
            if (textfield.getValue() == null || textfield.getValue().trim().isEmpty()) {
                val = 0.0f;
            } else {
                try { val = Float.parseFloat(textfield.getValue().trim()); } catch (NumberFormatException e) { val = 0.0f; }
            }
            ExtraDataStorage.setFloat(stats, 2, val);
            stats.combatRegen = Math.round(val);
            ci.cancel();
        }
    }
}