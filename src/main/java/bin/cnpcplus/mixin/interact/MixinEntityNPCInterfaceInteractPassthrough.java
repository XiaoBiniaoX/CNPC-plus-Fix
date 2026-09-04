package bin.cnpcplus.mixin.interact;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.interact.NpcInteractProbe;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复「准星对着 NPC 时，弓 / 食物 / 药水等需要右键使用的物品没反应」。
 *
 * 现象（哈基彬原话）：无论有没有给 NPC 设置右键交互功能，在一定距离内准星
 * 对准 NPC 使用弓或者食物药水等需要右击使用的物品时，右键无反应。此问题
 * CNPC 一直存在。
 *
 * 短路链（全部字节码实证）：
 *   EntityNPCInterface.processInteract 返回 true
 *     → EntityLiving.processInitialInteract 返回 true
 *       → EntityPlayer.interactOn 返回 SUCCESS
 *         → PlayerControllerMP.interactWithEntity 返回 SUCCESS
 *           → Minecraft.rightClickMouse 在 offset 150 直接 return
 *             → offset 307 之后的 processRightClick 永不执行
 *
 * 而 processInteract 有两处**无条件** true：
 *  - 客户端分支：只判 {@code !isAttacking()}，根本不看有没有交互内容
 *  - 服务端分支末尾那句 return true 在 if/else 链之外，
 *    四条路（任务交付 / 对话 / 角色 / 交互台词）全空转也照样返回 true
 * 另外副手也是无条件 true，且该分支之后不执行任何逻辑。
 *
 * 因为 rightClickMouse 是纯客户端逻辑、读的是客户端返回值，
 * 所以修复必须落在客户端；改服务端返回值对这个症状无效。
 *
 * 修法（HEAD 拦截，只在确定「无事可做」时返回 false）：
 *  1. 副手：CNPC 在该分支不做任何事，直接放行。
 *  2. 主手且客户端：手上有物品、且探针确认这只 NPC 没有任何交互内容时放行。
 *
 * 安全性：
 *  - {@code PlayerControllerMP.interactWithEntity} 是先无条件发
 *    {@code CPacketUseEntity} 再算返回值（字节码 offset 4..17 在 offset 39
 *    之前），所以返回 false **不会**让服务端少处理一次交互 ——
 *    对话、任务、角色界面仍由服务端正常打开。
 *  - 只在 {@code world.isRemote} 分支动手，服务端逻辑一行不改。
 *  - 探针保守判定，拿不准就维持原版行为。
 *  - 提供 config 开关 {@code interact.interactPassthrough}，
 *    万一与其他改右键的 mod 冲突可直接关掉。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceInteractPassthrough {

    @Inject(method = "func_184645_a", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$allowItemUse(EntityPlayer player, EnumHand hand,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!CnpcPlusConfig.isInteractPassthroughEnabled()) {
            return;
        }
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        // 只处理客户端：决定 processRightClick 是否执行的就是客户端返回值。
        // 这一判断放在最前面，确保服务端逻辑绝对不受影响
        // （即使将来被误注册进 common 数组也是安全的）。
        if (!npc.isRemote()) {
            return;
        }
        if (player == null) {
            return;
        }
        // 副手：原版在该分支无条件 return true 却什么都不做，纯粹白吞一次右键。
        if (hand != EnumHand.MAIN_HAND) {
            cir.setReturnValue(false);
            return;
        }
        ItemStack held = player.getHeldItem(hand);
        // 空手时保持原版行为，避免影响空手右键 NPC 的既有手感。
        if (held == null || held.isEmpty()) {
            return;
        }
        if (NpcInteractProbe.hasNothingToInteract(npc)) {
            cir.setReturnValue(false);
        }
    }
}
