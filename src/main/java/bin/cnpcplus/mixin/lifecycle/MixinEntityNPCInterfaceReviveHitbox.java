package bin.cnpcplus.mixin.lifecycle;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修「NPC 死亡再次复活后偶尔模型与碰撞箱不一致」（哈基彬需求 C-2 后半）。
 *
 * <h3>为什么是「偶尔」</h3>
 * {@code updateHitbox}（反编译 1178-1198）算尺寸依赖三个量：
 * {@code currentAnimation}、{@code deathTime}、{@code isKilled()}。
 * 前两个是**普通字段**，第三个走 datawatcher。
 * 而客户端只有一处会在复活时刷新碰撞箱 —— {@code func_70071_h_} 第 454-457 行：
 * <pre>
 * if (world.isRemote &amp;&amp; wasKilled != isKilled()) { deathTime = 0; updateHitbox(); }
 * </pre>
 * 这是**只在 isKilled 翻转那一刻触发的一次性刷新**。
 *
 * 问题在于 {@code IsDead}（datawatcher）与 {@code Animation}（另一个 datawatcher）
 * 不保证同一个网络包同时到达。如果 {@code IsDead} 先到、{@code Animation} 晚一 tick：
 * <ol>
 *   <li>{@code IsDead} 变 false → 触发这次唯一的刷新；</li>
 *   <li>但此刻 {@code currentAnimation} 还是 2（死亡动画），
 *       于是 {@code updateHitbox} 走第 1179-1181 行的**尸体尺寸 0.8 × 0.4**；</li>
 *   <li>下一 tick {@code Animation} 才变成 0，模型恢复站姿，
 *       可是刷新的机会已经用掉了，没有任何代码会再调 {@code updateHitbox}；</li>
 *   <li>结果：模型是活的，碰撞箱是躺着的 0.8 × 0.4 —— 一直错到下次
 *       有别的东西（改体型、上下坐骑、再死一次）碰巧触发刷新。</li>
 * </ol>
 * 两个 datawatcher 谁先到取决于网络时序，所以症状是「偶尔」。
 *
 * <h3>修法</h3>
 * 客户端每 tick 记住 {@code currentAnimation}，一旦变化就补一次
 * {@code updateHitbox()}。这样无论两个同步值以什么顺序到达，
 * 最终都会以正确的动画状态重算一次尺寸。
 *
 * <h3>为什么不改动那段 454-457</h3>
 * 它本身是对的（isKilled 翻转确实该刷新），只是不够 —— 缺的是
 * 「动画变化也该刷新」。补一个独立条件比改写原有条件更小、更不容易出错。
 *
 * <h3>开销</h3>
 * 每 tick 一次 int 比较。只有在动画真正变化的那一 tick 才会调
 * {@code updateHitbox}，而动画变化本来就是低频事件（死亡、复活、坐下等）。
 * 仅客户端执行，服务端 {@code reset()} 第 1245 行已经自己调了 updateHitbox。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceReviveHitbox {

    /** 上一 tick 看到的动画值。-1 = 尚未采样。 */
    @Unique private int cnpcplus$lastAnimation = -1;

    @Inject(method = "func_70071_h_", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$syncHitboxWithAnimation(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        // 服务端不需要：reset() 与 setDead() 都已显式调用 updateHitbox。
        if (npc.world == null || !npc.world.isRemote) return;

        int animation = npc.currentAnimation;
        if (this.cnpcplus$lastAnimation == animation) return;

        boolean first = this.cnpcplus$lastAnimation == -1;
        this.cnpcplus$lastAnimation = animation;
        // 首次采样不刷新：那不是「变化」，且实体刚创建时尺寸本来就是对的。
        if (first) return;

        npc.updateHitbox();
    }
}
