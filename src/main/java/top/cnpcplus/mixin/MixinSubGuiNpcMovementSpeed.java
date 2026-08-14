package top.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcMovement;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.WalkingSpeedAccess;

@Mixin(value = SubGuiNpcMovement.class, remap = false)
public class MixinSubGuiNpcMovementSpeed {
    @Shadow(remap = false) private DataAI ai;

    @Inject(method = "m_7856_", at = @At("TAIL"))
    private void cnpcplus$floatSpeedField(CallbackInfo ci) {
        SubGuiNpcMovement self = (SubGuiNpcMovement) (Object) this;
        GuiTextFieldNop field = self.getTextField(14);
        if (field == null) return;
        field.numbersOnly = false;
        field.setFloatsOnly();
        field.setMinMaxDefault(0.01f, 100.0f, 5.0f);
        field.setValue(Float.toString(((WalkingSpeedAccess) this.ai).cnpcplus$getWalkingSpeed()));
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$saveFloatSpeed(GuiTextFieldNop field, CallbackInfo ci) {
        if (field.id != 14) return;
        ((WalkingSpeedAccess) this.ai).cnpcplus$setWalkingSpeed(field.getFloat());
        ci.cancel();
    }
}
