package top.cnpcplus.mixin;

import noppes.npcs.controllers.data.DataTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 238-5：存档里 TransformEditingModus 残留 true 会锁死昼夜切换
 * （{@code EntityAITransform:17} 的 {@code !npc.transform.editingModus} 恒假）。
 * 读档后清 false——编辑器改的是内存对象，不走 readToNBT，不受影响。
 */
@Mixin(value = DataTransform.class, remap = false)
public class MixinDataTransformClearEditing {

    @Shadow(remap = false)
    public boolean editingModus;

    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$clearEditingModus(CallbackInfo ci) {
        this.editingModus = false;
    }
}
