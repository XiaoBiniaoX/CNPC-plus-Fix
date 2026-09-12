package bin.cnpcplus.mixin.corpse;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 NPC 死亡后复活偶尔出现模型与碰撞箱不一致。
 *
 * <p>原版 {@code reset()}（EntityNPCInterface:1210-1259）有三处配合缺陷：
 * <ul>
 *   <li>:1237 直接写字段 {@code currentAnimation = 0}，没走 {@code setCurrentAnimation}，
 *       所以不保证产生 synched data 更新；而 :1218 的 {@code entityData.set(Animation, 0)}
 *       在值本来就是 0 时不会发同步包，客户端的 {@code onSyncedDataUpdated} 也就不会
 *       {@code refreshDimensions}，客户端就停在死亡时的躺卧尺寸上；</li>
 *   <li>{@code deathTime} 是普通字段而非 synched data，而 {@code getDimensions}（:1176）
 *       把 {@code deathTime > 0} 也算作躺卧尺寸，客户端只能靠自己 tick 清零；</li>
 *   <li>:1247 之后的补救同步被 {@code needsSync}（即 {@code hasDied}）挡着。而
 *       {@code hurt}/{@code outOfLevel} 那条路径（:717-719）会直接调 {@code reset()}，
 *       此时 {@code hasDied} 为 false，一个同步包都不发 —— 这正是「偶发」的来源。</li>
 * </ul>
 *
 * <p>修法：在 {@code reset()} 末尾服务端侧强制走一次带同步的动画重置 + 尺寸刷新 + 客户端更新，
 * 不再依赖 {@code needsSync}。客户端的 {@code readSpawnData} 末尾自带 refreshDimensions。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceRespawnSize {

    @Inject(method = "reset", at = @At("TAIL"))
    private void cnpcplus$syncSizeOnRespawn(CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.level() == null || self.level().isClientSide) return;

        // 走 setter 而不是写字段，确保 synched Animation 被真正写入并下发。
        self.setCurrentAnimation(0);
        // 顺序不能反：getDimensions 会读 deathTime，先清零再刷新才拿得到活着的尺寸。
        self.deathTime = 0;
        self.refreshDimensions();
        // 无条件下发一次，补上 needsSync 为 false 时那条完全不发包的路径。
        self.updateClient = true;
    }
}
