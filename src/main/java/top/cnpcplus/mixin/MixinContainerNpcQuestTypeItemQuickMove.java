package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerNpcQuestTypeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A4: 任务-收集任务编辑槽位 shift+左键崩溃。
 * 根因：ContainerNpcQuestTypeItem.quickMoveStack(m_7648_) 返回 null，
 * 客户端 doClick 的 QUICK_MOVE 分支对 null 调 isEmpty() → NPE("itemstack8 is null")。
 * 修复：返回 ItemStack.EMPTY（与原版 ContainerNpcQuestReward 行为一致，shift 无副作用，不崩）。
 */
@Mixin(value = ContainerNpcQuestTypeItem.class, remap = false)
public class MixinContainerNpcQuestTypeItemQuickMove {

    @Inject(method = "m_7648_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$noNullQuickMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(ItemStack.EMPTY);
    }
}
