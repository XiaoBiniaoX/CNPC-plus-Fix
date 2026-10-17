package top.cnpcplus.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;
import top.cnpcplus.stats.HideDelayStore;

/** 保留替身实例的真实尺寸（包括 Gecko 自定义宽高），只在死亡时缩小宿主碰撞箱。 */
@Mixin(value = EntityCustomNpc.class, remap = false)
public abstract class MixinEntityCustomNpcDimensions {
    @Inject(method = "m_6972_", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$corpseDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        EntityCustomNpc npc = (EntityCustomNpc) (Object) this;
        if (npc.ais == null || npc.stats == null || npc.display == null) return;
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.KilledBodyNoHitbox, true)) return;
        if (!npc.isKilled() && npc.deathTime <= 0) return;
        // 238-2：与渲染隐藏同步的延迟门——deathTime 未到配置 tick 前保持正常碰撞箱，
        // 避免「箱子已压扁但尸体还在」的观感分叉。死亡瞬间 isKilled=true/deathTime=0 时不压。
        if (npc.deathTime < HideDelayStore.get(npc.stats)) return;
        EntityDimensions size = cir.getReturnValue();
        if (size != null) cir.setReturnValue(EntityDimensions.scalable(1.0E-5f, size.height));
    }

    /**
     * 外部替身分支不经过基类的睡姿尺寸刷新；getDimensions 的返回值不等于已应用的 AABB。
     * 在宿主 tick 结束统一校准，尺寸变化才刷新，并用 setPos 消除 vanilla 扩箱角点偏移。
     *
     * <p>238-6：死亡期间（isKilled||deathTime>0）无条件校准，不再只跑 hasEntity()
     * 分支——纯人形 NPC 服务端 box 依赖死亡瞬间一次 refresh，两端/状态分叉时
     * 客户端是「一条线」而服务端还是全箱。活着时保持原条件防抖。
     */
    @Inject(method = "m_8119_", at = @At("TAIL"))
    private void cnpcplus$refreshModelBounds(CallbackInfo ci) {
        EntityCustomNpc npc = (EntityCustomNpc) (Object) this;
        boolean dying = npc.isKilled() || npc.deathTime > 0;
        if (!dying && (npc.modelData == null || !npc.modelData.hasEntity())) return;
        EntityDimensions size = npc.getDimensions(npc.getPose());
        if (npc.getBbWidth() != size.width || npc.getBbHeight() != size.height) {
            double x = npc.getX(), y = npc.getY(), z = npc.getZ();
            npc.refreshDimensions();
            npc.setPos(x, y, z);
        }
    }
}
