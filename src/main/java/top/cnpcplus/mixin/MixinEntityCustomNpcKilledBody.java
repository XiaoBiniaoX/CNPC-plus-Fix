package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

/**
 * {@code EntityCustomNpc} 侧的「尸体不推挤」。
 *
 * <h3>为什么必须单独一份</h3>
 * {@code EntityCustomNpc} **覆写**了 {@code m_6138_}（pushEntities，反编译 148-157 行），
 * 里面除了 {@code hitboxState} 判断还多了一段「客户端不可见 NPC 跳过推挤」的逻辑。
 * 打在 {@code EntityNPCInterface} 上的注入对 {@code EntityCustomNpc} 实例
 * 根本不会触发 —— 而 {@code EntityCustomNpc} 恰好是玩家实际用的那个 NPC 类型。
 *
 * <p>逻辑与 {@link MixinEntityNPCKilledBody} 的推挤部分相同，只是目标类不同。
 * 刻意不抽公共方法：mixin 之间不能互相引用，抽出去就得放 mixin 包外多一个文件，
 * 而这里只有两行判断，重复它比多一层间接更省。
 *
 * <p>尸体的**碰撞箱**（宽度压到 1e-5）不在这里做：{@code EntityCustomNpc} 也覆写了
 * {@code m_6972_}，那部分与替身模型的维度计算耦合，统一放在
 * {@code MixinEntityCustomNpcDimensions} 里 —— 两个 mixin 注入同一方法 RETURN 时
 * 执行顺序没有保证，合并到一处顺序才确定。
 */
@Mixin(value = EntityCustomNpc.class, remap = false)
public abstract class MixinEntityCustomNpcKilledBody {

    @Inject(method = "m_6138_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$deadBodyNoPush(CallbackInfo ci) {
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.KilledBodyNoPush, true)) return;
        if (((EntityCustomNpc) (Object) this).isKilled()) {
            ci.cancel();
        }
    }
}
