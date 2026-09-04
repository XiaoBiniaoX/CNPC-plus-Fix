package top.cnpcplus.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.interact.InteractPriority;

/**
 * 修复「准星对准 NPC 时右键用不了弓/食物/药水」。
 *
 * <p>机制（字节码逐级实证）：
 * <ol>
 *   <li>{@code Minecraft.startUseItem} 的 ENTITY 分支（offset 176-288）先调
 *       {@code interactAt}，不 consumesAction 再调 {@code interact}；两者都不消费时
 *       {@code ifeq 405} 会跳到 offset 442 继续执行 {@code useItem} ——
 *       所以只要交互链返回 PASS/FAIL，客户端本身**不会**吞掉物品使用。</li>
 *   <li>{@code Player.interactOn}（offset 54-140）调 {@code Entity.interact}，
 *       只要它 consumesAction 就直接 return，后面的
 *       {@code itemstack.interactLivingEntity} 与整条使用物品链全被跳过。</li>
 *   <li>{@code EntityNPCInterface.m_6071_} 正常路径最后 offset 338 返回 **PASS**，
 *       两处 SUCCESS 都被 CNPC 工具物品守卫（cloner/wand/mount/scripter/moving）。</li>
 * </ol>
 *
 * <p>那问题出在哪：{@code Mob.interact}（offset 82-89）在 {@code mobInteract} 返回
 * 不消费时，**还会继续往下走原版 Mob 的后续分支**；更关键的是
 * {@code EntityDialogNpc} 重写了 {@code m_6071_}（javap 确认它是唯一另一个重写者），
 * 以及各 role 的 {@code interact} 会 {@code openDialog} / 开容器。一旦服务端在这次
 * 右键里给玩家打开了 GUI，客户端随后的 {@code useItem} 就落在「手上正忙」
 * （{@code LocalPlayer.isHandsBusy}，startUseItem offset 19-22 直接 return）或被
 * 新打开的界面吃掉 —— 表现就是「对着 NPC 按右键，弓一点反应都没有」。
 *
 * <p>修法：在 {@code m_6071_} 的 HEAD 判断这次右键是否应让物品优先，若是则直接返回
 * PASS 取消掉整个 NPC 交互。PASS 不消费动作，于是：
 * <ul>
 *   <li>服务端不会开对话/商店，玩家「手上不忙」；</li>
 *   <li>客户端 startUseItem 的 ENTITY 分支不消费 → 落到 offset 442 的 {@code useItem}；</li>
 *   <li>弓/食物/药水正常进入使用状态。</li>
 * </ul>
 *
 * <p>判据全部收在 {@link InteractPriority}，默认只对「本身就有右键使用动作」的物品生效，
 * 且潜行右键与 CNPC 工具一律不抢，所以对话/商店/任务/编辑功能都不受影响。
 * 配置项 {@code InteractItemPriority} 可整体关闭恢复原版。
 *
 * <p>注入 HEAD 而非改写方法体：原版逻辑完全保留，与其他改 NPC 交互的模组可共存。
 * 双端一致：{@code m_6071_} 两端都会跑，这里两端用同一判据，避免客户端预测与服务端不符。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInteractPriority {

    @Inject(method = "m_6071_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$letItemWin(Player player, InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (InteractPriority.itemWins(player, self, hand)) {
            // PASS = 不消费动作，原版随后会去执行「使用手上物品」。
            // 绝不能返回 FAIL：FAIL 同样不消费，但会让客户端 startUseItem 的
            // BLOCK 分支语义混乱，且部分模组把 FAIL 当作「明确拒绝」处理。
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
