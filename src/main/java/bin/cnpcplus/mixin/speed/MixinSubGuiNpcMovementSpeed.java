package bin.cnpcplus.mixin.speed;

import bin.cnpcplus.speed.WalkingSpeedFloatAccess;
import noppes.npcs.client.gui.SubGuiNpcMovement;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcMovement.class, remap = false)
public class MixinSubGuiNpcMovementSpeed {
    @Shadow(remap = false) private DataAI ai;

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void cnpcplus$showFloatSpeed(CallbackInfo ci) {
        SubGuiNpcMovement self = (SubGuiNpcMovement) (Object) this;
        GuiNpcTextField field = self.getTextField(14);
        if (field == null) return;
        field.numbersOnly = false;
        field.setText(Float.toString(((WalkingSpeedFloatAccess) this.ai).cnpcplus$getWalkingSpeedFloat()));
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$saveFloatSpeed(GuiNpcTextField field, CallbackInfo ci) {
        SubGuiNpcMovement self = (SubGuiNpcMovement) (Object) this;
        if (self.getTextField(14) != field) return;
        try {
            ((WalkingSpeedFloatAccess) this.ai).cnpcplus$setWalkingSpeed(Float.parseFloat(field.getText()));
        } catch (RuntimeException ignored) {
            field.setText(Float.toString(((WalkingSpeedFloatAccess) this.ai).cnpcplus$getWalkingSpeedFloat()));
        }
        ci.cancel();
    }
}
