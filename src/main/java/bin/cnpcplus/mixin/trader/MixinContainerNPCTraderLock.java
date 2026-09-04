package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.trader.SlotNpcTraderDisplay;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 修复商人交易界面可以直接取走 / 放入「商品展示槽」的漏洞。
 *
 * 现象：在商人交易页面按住 Ctrl（或装了背包整理类 mod）能把兑换栏里的物品
 * 直接拿走，也能把自己背包的东西放进兑换栏。原版 CNPC 就有此问题。
 *
 * 根因（证据）：
 *  - {@code ContainerNPCTrader.<init>} 给 index 0..17 用的是裸
 *    {@code net.minecraft.inventory.Slot}，canTakeStack 恒 true、
 *    isItemValid 委派给恒 true 的 {@code NpcMiscInventory}。
 *  - {@code ContainerNPCTrader.slotClick} 对非 PICKUP 只是 return EMPTY
 *    （「不做交易」），并不阻止原版把物品搬走。
 *  - 原版 {@code Container.slotClick} 的 QUICK_MOVE / SWAP / THROW /
 *    PICKUP_ALL / QUICK_CRAFT / PICKUP 分支都会查 canTakeStack，
 *    唯一绕过它的是 CLONE（只判创造模式）。
 *
 * 因此采用双层防护，与 1.21.1Neo 已验证的做法一致：
 *  1. 构造器 TAIL：把 index 0..17 原地替换成 {@link SlotNpcTraderDisplay}，
 *     保留原坐标、原 slotNumber 与原 inventory index —— 不增删槽位，
 *     以免破坏分页同步（MixinContainerNPCTraderSync）与客户端 GUI 布局。
 *  2. slotClick HEAD：对 index 0..17 的非 PICKUP 点击直接返回 EMPTY，
 *     补上 CLONE 这个槽位层管不到的缺口。
 *
 * 顺带修掉「刚兑换的物品短时间内放不进背包」：那不是冷却（容器与 RoleTrader
 * 里没有任何 tick 计数或时间戳），而是服务端 {@code NetHandlerPlayServer}
 * 发现 slotClick 返回值与客户端预测不一致时发 accepted=false 并强制
 * detectAndSendChanges 造成的整容器回滚。商品槽被提前拦下后两端预测一致，
 * 回弹随之消失。
 *
 * 服务端安全性：本混入不改变交易结算逻辑，只做「更严格的拒绝」，
 * 且构造器在两端都会执行，客户端与服务端的槽位类型保持一致，不产生协议错位。
 */
@Mixin(value = ContainerNPCTrader.class, remap = false)
public class MixinContainerNPCTraderLock {

    /** 商品展示槽数量，与原版构造器的 {@code for (int i = 0; i < 18; ++i)} 一致。 */
    private static final int CNPCPLUS_DISPLAY_SLOTS = 18;

    /**
     * 原地替换前 18 个槽，保留坐标与索引。
     *
     * 用 {@code List.set} 而不是清空重建，是为了让 slotNumber 保持不变 ——
     * 网络层的槽位点击、分页同步和客户端绘制都依赖这个编号。
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$lockDisplaySlots(EntityNPCInterface npc, EntityPlayer player, CallbackInfo ci) {
        ContainerNPCTrader self = (ContainerNPCTrader) (Object) this;
        List<Slot> slots = self.inventorySlots;
        if (slots == null) {
            return;
        }
        int count = Math.min(CNPCPLUS_DISPLAY_SLOTS, slots.size());
        for (int i = 0; i < count; i++) {
            Slot old = slots.get(i);
            if (old == null || old instanceof SlotNpcTraderDisplay) {
                continue;
            }
            SlotNpcTraderDisplay replacement = new SlotNpcTraderDisplay(
                    old.inventory, old.getSlotIndex(), old.xPos, old.yPos);
            replacement.slotNumber = old.slotNumber;
            slots.set(i, replacement);
        }
    }

    /**
     * 容器层兜底：商品槽只允许 PICKUP（购买）路径。
     *
     * CLONE 在原版实现里只检查创造模式、完全不查 canTakeStack，
     * 所以必须在这里堵住，否则创造模式下仍可无视槽位限制复制商品。
     */
    @Inject(method = "func_184996_a", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$rejectNonPickup(int slotId, int button, ClickType clickType,
                                          EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        if (slotId >= 0 && slotId < CNPCPLUS_DISPLAY_SLOTS && clickType != ClickType.PICKUP) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
