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
        // 下限必须是 0：GuiTextFieldNop.unFocused() 会在回调我们之前按 minF 夹值，
        // 旧的 0.01f 下限让玩家输入的 0 在到达数据层之前就被改写成 0.01 —— 这是
        // 3.3.0「移速无法设为 0」的直接拦路点（数据层的哨兵问题另在 MixinDataAIWalkingSpeed 修）。
        field.setMinMaxDefault(0.0f, 100.0f, 5.0f);
        field.setValue(Float.toString(((WalkingSpeedAccess) this.ai).cnpcplus$getWalkingSpeed()));
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$saveFloatSpeed(GuiTextFieldNop field, CallbackInfo ci) {
        if (field.id != 14) return;
        float speed = field.getFloat();
        // 兜底夹值：setWalkingSpeed 对非法值会 throw CustomNPCsException，
        // 而这里在 GUI 事件线程上，异常会把整个界面打断。宁可静默夹到合法区间。
        if (!Float.isFinite(speed) || speed < 0.0f) speed = 0.0f;
        if (speed > 100.0f) speed = 100.0f;
        ((WalkingSpeedAccess) this.ai).cnpcplus$setWalkingSpeed(speed);
        ci.cancel();
    }
}
