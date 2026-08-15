package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.ai.AiSpeedAccess;
import noppes.npcs.client.gui.SubGuiNpcMovement;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcMovement.class, remap = false)
public abstract class MixinSubGuiNpcMovementSpeed {
    @Shadow
    private DataAI ai;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$enableFloatSpeed(CallbackInfo ci) {
        GuiTextFieldNop field = ((SubGuiNpcMovement) (Object) this).getTextField(14);
        if (field == null) return;
        field.numbersOnly = false;
        field.setFloatsOnly();
        field.setMinMaxDefault(0.01F, 100.0F, 5.0F);
        field.setValue(Float.toString(((AiSpeedAccess) this.ai).cnpcplus$getWalkingSpeed()));
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$saveFloatSpeed(GuiTextFieldNop field, CallbackInfo ci) {
        if (field.id != 14) return;
        ((AiSpeedAccess) this.ai).cnpcplus$setWalkingSpeed(field.getFloat());
        ci.cancel();
    }
}
