package bin.cnpcplus.mixin.corpse;

import bin.cnpcplus.corpse.CorpseHitbox;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 会重生的 NPC 死亡后，尸体不再有碰撞箱。
 *
 * <p>原版只在勾了「隐藏尸体」时把宽度压成 1e-5，高度始终保留
 * （EntityNPCInterface.getDimensions:1182-1186），而 {@code hideKilledBody} 默认关，
 * 所以默认配置下尸体一定挡路。这里把宽高一起压掉。
 *
 * <p>复活时 {@code isKilled()} 变 false，判定自动失效，尺寸随 refreshDimensions 恢复，
 * 不需要额外的还原代码。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceCorpse {

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$corpseNoHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!CorpseHitbox.isRestingCorpse((EntityNPCInterface) (Object) this)) return;
        cir.setReturnValue(EntityDimensions.scalable(1.0E-5f, 1.0E-5f));
    }
}
