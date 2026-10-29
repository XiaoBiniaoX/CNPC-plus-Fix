package top.cnpcplus.villagertrade;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import bin.cnpcplus.mixin.villagertrade.MerchantMenuAccessor;
import bin.cnpcplus.mixin.villagertrade.MerchantContainerAccessor;
import top.cnpcplus.roles.TradeData;
import java.util.Optional;

public class EditableMerchantMenu extends MerchantMenu {

    private final Merchant merchant;
    private final Runnable onChange;
    private boolean syncing = false;
    /** EditMode 下最近一次点条目按钮的索引，GUI 读后清零（选中语义只认真点击）。 */
    public int entryClickIndex = -1;

    public EditableMerchantMenu(int containerId, Inventory playerInventory, Merchant merchant, Runnable onChange) {
        super(containerId, playerInventory, merchant);
        this.merchant = merchant;
        this.onChange = onChange;
    }

    public int takeEntryClick() {
        int i = entryClickIndex;
        entryClickIndex = -1;
        return i;
    }

    @Override
    public void broadcastChanges() {
    }

    @Override
    public void sendAllDataToRemote() {
    }

    /**
     * 编辑模式拦截：postButtonClick（点击条目）→ tryMoveItems 会 ①moveFromInventoryToPaymentSlot
     * 从背包偷抽兑换物 ②把支付槽物品推回背包 ③用 offer 覆盖编辑中的槽——
     * 这就是"点条目时背包兑换物幽灵动"的元凶。编辑面板不需要交易预填。
     */
    @Override
    public void tryMoveItems(int index) {
        if (EditMode.active) {
            entryClickIndex = index;
            return;
        }
        super.tryMoveItems(index);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.QUICK_MOVE) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * 把当前槽位内容写回指定条目。索引必须由调用方显式传入（= GUI 的 lastShopItem）：
     * 原实现读 container.getSelectionHint()，而 selectionHint 是 vanilla
     * postButtonClick 写入的（scrollOff 陈旧时=越界值）→ 三索引背离导致
     * "sync skip" 静默丢写（根因A）。
     */
    public void syncFromSlots(int idx) {
        if (syncing) return;
        syncing = true;
        try {
            MerchantContainer container = ((MerchantMenuAccessor) this).getTradeContainer();
            MerchantOffers offers = merchant.getOffers();
            if (idx >= 0 && idx < offers.size()) {
                MerchantOffer old = offers.get(idx);
                ItemStack costA = container.getItem(0).copy();
                ItemStack costB = container.getItem(1).copy();
                ItemStack result = container.getItem(2).copy();
                MerchantOffer newOffer = new MerchantOffer(TradeData.cost(costA),
                        costB.isEmpty() ? Optional.empty() : Optional.of(TradeData.cost(costB)),
                        result, old.getMaxUses(), old.getXp(), old.getPriceMultiplier());
                offers.set(idx, newOffer);
                if (onChange != null) onChange.run();
            } else {
                // 233d-UI9 日志清理：println→debug（默认不输出，只留 error 级）
                org.slf4j.LoggerFactory.getLogger("cnpcplus").debug(
                        "[vt] sync skip idx={} offers={}", idx, offers.size());
            }
        } finally {
            syncing = false;
        }
    }

    public void loadOfferToSlots(int index) {
        if (syncing) return;
        syncing = true;
        try {
            MerchantContainer container = ((MerchantMenuAccessor) this).getTradeContainer();
            MerchantOffers offers = merchant.getOffers();
            if (index >= 0 && index < offers.size()) {
                MerchantOffer offer = offers.get(index);
                container.setItem(0, offer.getCostA().copy());
                container.setItem(1, offer.getCostB().copy());
                container.setItem(2, offer.getResult().copy());
                ((MerchantContainerAccessor) container).setSelectionHint(index);
            }
        } finally {
            syncing = false;
        }
    }
}
