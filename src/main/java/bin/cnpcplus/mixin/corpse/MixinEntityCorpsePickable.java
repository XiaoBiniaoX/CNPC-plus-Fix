package bin.cnpcplus.mixin.corpse;

import bin.cnpcplus.corpse.CorpseHitbox;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 尸体不再被准星拾取、不再吃箭。
 *
 * <p>{@code LivingEntity.isPickable()} 只看 {@code isRemoved()}，而 CNPC 的会重生 NPC 死亡后
 * 走的是「不调 super.remove」的分支（EntityNPCInterface.remove:1411-1424），
 * 所以 {@code isRemoved()} 仍为 false，尸体照样能被准星选中、被投射物命中
 * （原版 {@code Entity.canBeHitByProjectile} = {@code isAlive() && isPickable()}）。
 * 这条与 AABB 大小无关，必须单独处理。
 *
 * <p>CNPC 自己没有声明 {@code isPickable}，所以注入目标是原版 {@code Entity}，
 * 用实例类型判断把作用范围限制在 CNPC 上。
 */
@Mixin(value = Entity.class, remap = false)
public class MixinEntityCorpsePickable {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$corpseNotPickable(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof EntityNPCInterface npc)) return;
        if (!CorpseHitbox.isRestingCorpse(npc)) return;
        cir.setReturnValue(false);
    }
}
