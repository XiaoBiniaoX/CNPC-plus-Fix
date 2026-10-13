package bin.cnpcplus.mixin.entity.data;

import noppes.npcs.controllers.VisibilityController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DataDisplay setVisible 后注册 trackNpc，修复运行期改可见性规则永不生效。
 *
 * <p>CNPC 原版只在 readToNBT 时调 trackNpc，运行期脚本改 visible 不注册，
 * VisibilityController 不会跟踪该 NPC → 可见性规则变更不生效。
 */
@Mixin(value = DataDisplay.class, remap = false)
public class MixinDataDisplayTrackNpc {

    @Shadow(remap = false)
    EntityNPCInterface npc;

    @Inject(method = "setVisible", at = @At("TAIL"), remap = false)
    private void cnpcplus$trackNpc(CallbackInfo ci) {
        if (this.npc != null && !this.npc.isClientSide()) {
            VisibilityController.instance.trackNpc(this.npc);
        }
    }
}
