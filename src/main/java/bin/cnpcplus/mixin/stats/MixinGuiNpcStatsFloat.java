package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.accessor.StatsFloatAccess;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.data.DataStats;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcStats.class, remap = false)
public class MixinGuiNpcStatsFloat {

    @Shadow
    private DataStats stats;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$initFloatFields(CallbackInfo ci) {
        GuiNpcStats self = (GuiNpcStats)(Object)this;
        StatsFloatAccess access = (StatsFloatAccess) stats;

        self.getTextField(0).numbersOnly = false;
        self.getTextField(0).setFloatsOnly().setMinMaxDefault(0.0f, 99999.0f, 20.0f);
        self.getTextField(0).setValue(String.format("%.1f", access.cnpcplus$getMaxHealth()));

        self.getTextField(14).numbersOnly = false;
        self.getTextField(14).setFloatsOnly().setMinMaxDefault(0.0f, 99999.0f, 1.0f);
        self.getTextField(14).setValue(String.format("%.1f", access.cnpcplus$getHealthRegen()));

        self.getTextField(16).numbersOnly = false;
        self.getTextField(16).setFloatsOnly().setMinMaxDefault(0.0f, 99999.0f, 0.0f);
        self.getTextField(16).setValue(String.format("%.1f", access.cnpcplus$getCombatRegen()));
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$unFocusedFloat(GuiTextFieldNop textfield, CallbackInfo ci) {
        GuiNpcStats self = (GuiNpcStats)(Object)this;
        if (textfield.id == 0) {
            float val = textfield.getFloat();
            if (val < 0.01f) val = 20.0f;
            ((StatsFloatAccess)stats).cnpcplus$setMaxHealth(val);
            ((GuiNPCInterface2)(Object)self).npc.heal(val);
            ci.cancel();
        } else if (textfield.id == 14) {
            float val = textfield.getFloat();
            if (val < 0.0f) val = 0.0f;
            ((StatsFloatAccess)stats).cnpcplus$setHealthRegen(val);
            ci.cancel();
        } else if (textfield.id == 16) {
            float val = textfield.getFloat();
            if (val < 0.0f) val = 0.0f;
            ((StatsFloatAccess)stats).cnpcplus$setCombatRegen(val);
            ci.cancel();
        }
    }
}