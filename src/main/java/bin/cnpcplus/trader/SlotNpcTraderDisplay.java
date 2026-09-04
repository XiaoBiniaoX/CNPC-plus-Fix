package bin.cnpcplus.trader;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 商人交易界面（玩家侧）的商品展示槽。
 *
 * 原版 {@code ContainerNPCTrader} 构造器给 index 0..17 用的是裸 {@link Slot}，
 * 默认 {@code canTakeStack()} 恒为 true、{@code isItemValid()} 委派给
 * {@code NpcMiscInventory.isItemValidForSlot()}（后者无条件返回 true）。
 * 于是这 18 个「商品展示」槽在原版点击路径下既能被拿走也能被放入 ——
 * 背包整理类 mod 发出的 QUICK_MOVE / SWAP / THROW / PICKUP_ALL 等点击
 * 会直接把商品搬进玩家背包。
 *
 * CNPC 自己其实写过一个带防护的 {@code SlotNpcTraderItems}，但它从未被
 * new 过（死代码），而且只重写了 isItemValid、缺 canTakeStack。这里补齐两者。
 *
 * 原版 {@code Container.slotClick} 的 QUICK_MOVE / SWAP / THROW /
 * PICKUP_ALL / QUICK_CRAFT / PICKUP 分支都会检查 canTakeStack，所以这一层
 * 覆盖绝大多数入口；唯一绕过它的 CLONE（仅创造模式）在容器层另行拦截。
 *
 * 交易本身走 {@code ContainerNPCTrader.slotClick} 的 PICKUP 分支，
 * 该分支直接读 {@code slot.getStack()} 并调用私有 givePlayer，
 * 不经过 canTakeStack，因此正常购买不受影响。
 */
public class SlotNpcTraderDisplay extends Slot {

    public SlotNpcTraderDisplay(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    /** 商品展示槽不接受玩家放入任何物品。 */
    @Override
    public boolean isItemValid(ItemStack stack) {
        return false;
    }

    /** 商品展示槽不允许被拿走。 */
    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return false;
    }
}
