package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AI-旋转设置后立即同步实体 yaw，避免等下一次 AI 看向目标才生效。 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCImmediateRotation {
    @Unique private int cnpcplus$lastOrientation = Integer.MIN_VALUE;
    @Unique private int cnpcplus$lastStanding = Integer.MIN_VALUE;
    @Inject(method = "m_8107_", at = @At("TAIL"))
    private void cnpcplus$applyStandingRotation(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.ais == null) return;
        int standing = npc.ais.getStandingType();
        boolean changed = cnpcplus$lastOrientation != npc.ais.orientation || cnpcplus$lastStanding != standing;
        cnpcplus$lastOrientation = npc.ais.orientation;
        cnpcplus$lastStanding = standing;
        // 只在设置变化时立即应用，不每 tick 抢走战斗、寻路或交互的朝向。
        if (!changed || (standing != 1 && standing != 3)) return;
        float rotation = npc.ais.orientation;
        npc.yRotO = rotation;
        npc.setYRot(rotation);
        npc.yBodyRotO = rotation;
        npc.yBodyRot = rotation;
        npc.yHeadRotO = rotation;
        npc.yHeadRot = rotation;
    }
}
