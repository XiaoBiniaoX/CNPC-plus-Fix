package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.common.RespawnCycleStore;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 收尾 {@code updateHitbox} 与 {@code reset}。
 *
 * <h3>为什么必须重建 AABB（阶段 22 第 3 项）</h3>
 * 原版 {@code updateHitbox}（反编译 1178-1198）**直接写 {@code field_70130_N} /
 * {@code field_70131_O}（width/height 字段），从不调 {@code func_70105_a}（setSize）**。
 * 全类唯一的 setSize 在构造期第 395 行。而只有 setSize 才会同步重算包围盒，
 * 直接写字段不会。末尾第 1197 行的 {@code setPosition} 虽然会按当前 width/height
 * 重设 AABB，但它算的是「以 posY 为底」，且子类
 * {@code EntityCustomNpc.updateHitbox} 还会在 super 之后对 passengers 递归，
 * 顺序上不可靠。所以在 TAIL 显式重建一次，这也正是哈基彬所说
 * 「npc 死亡再次复活后偶尔模型与碰撞箱不一致」的根治点 —— 那个「偶尔」
 * 就是这条竞态。
 *
 * <h3>尸体碰撞箱（哈基彬需求 C-2）</h3>
 * 见 {@link #cnpcplus$removeCorpseHitbox}。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceHitbox implements bin.cnpcplus.common.INpcGroundNavigation {
    @Inject(method = "reset", at = @At("TAIL"), remap = false)
    private void cnpcplus$restoreSpawnCycle(CallbackInfo ci) {
        RespawnCycleStore.restore((EntityNPCInterface) (Object) this);
    }

    /**
     * 先去掉尸体碰撞箱（需求 C-2 前半），再把最终尺寸落到包围盒上。
     *
     * <h3>为什么两件事写在同一个 handler 里</h3>
     * 顺序有依赖：必须先改完 width，再据此重建 AABB。而同一方法上的多个
     * {@code @Inject} 谁先执行并没有保证，拆成两个 handler 会引入运气成分。
     * 合成一个，顺序就是代码顺序。
     *
     * <h3>原版为什么会有尸体碰撞箱</h3>
     * {@code updateHitbox} 第 1179-1181 行：{@code currentAnimation == 2}
     * （死亡动画）或 {@code deathTime > 0} 时把尺寸设成 **width 0.8 / height 0.4** ——
     * 宽度反而比常态 0.6 更宽。第 1191-1193 行虽然有一段缩小碰撞箱的逻辑，
     * 但条件是 {@code !display.getHasHitbox() || isKilled() && stats.hideKilledBody}，
     * 也就是**只在「隐藏尸体」开着时**才生效，而且只改 width 不改 height。
     * 所以默认配置下尸体是个 0.8 宽的实心障碍物，横在死亡地点挡路。
     *
     * <h3>为什么限定「返回起点为开」</h3>
     * 哈基彬要的是「固定点位重生的 npc」。判据取 {@code ais.returnToStart}
     * （AI 设置里的「返回起点」开关，{@code DataAI.java:53}，NBT 键 {@code ReturnToStart}）：
     * 它为真时 {@code reset()} 第 1235-1237 行会把 NPC 传回起点，
     * 也就是「死在哪里都会回到固定点位重生」。这类 NPC 的尸体必然留在
     * 与重生点无关的位置，挡路且无意义。
     * 不用 {@code stats.spawnCycle} 做判据：那是「什么时段重生」，
     * 与「在哪里重生」无关，拿它当条件会影响到不该影响的 NPC。
     *
     * <h3>为什么只改 width</h3>
     * 与原版第 1192 行的既有写法保持一致（它也只把 width 设成 1.0E-5f）。
     * 宽度趋零后包围盒退化成一条竖线，实体碰撞与射线命中都不再拦人；
     * 保留 height 则让原版渲染与
     * {@code RenderNPCInterface.java:163} 的尸体判定不受干扰。
     */
    @Inject(method = "updateHitbox", at = @At("TAIL"), remap = false)
    private void cnpcplus$refreshBoundingBox(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        Entity entity = (Entity) npc;

        // 尸体去碰撞箱：只对「返回起点为开」的 NPC 生效。
        if (npc.ais != null && npc.ais.returnToStart) {
            // 与原版第 1179 行同一套「是不是尸体」的判据。
            // deathTime 在 EntityLivingBase 上（不是 Entity），所以从 npc 读。
            boolean corpse = npc.currentAnimation == 2
                    || npc.currentAnimation == 7
                    || npc.deathTime > 0
                    || npc.isKilled();
            if (corpse) {
                entity.width = 1.0E-5f;
            }
        }

        float width = entity.width;
        float height = entity.height;
        npc.setEntityBoundingBox(new AxisAlignedBB(npc.posX - width / 2.0, npc.posY,
            npc.posZ - width / 2.0, npc.posX + width / 2.0,
            npc.posY + height, npc.posZ + width / 2.0));
    }
}
