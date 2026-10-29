package top.cnpcplus.villagertrade;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * 可编辑的 Merchant 实现。
 * 编辑面板直接复用原版村民交易界面（MerchantScreen），物品放上即写回 offer。
 */
public class EditableMerchant implements Merchant {

    private final MerchantOffers offers;
    private final Runnable onChange;
    @Nullable
    private Player tradingPlayer;

    public EditableMerchant(MerchantOffers offers, Runnable onChange) {
        this.offers = offers;
        this.onChange = onChange;
    }

    public MerchantOffers getOffers() {
        return this.offers;
    }

    /** 新增一条空交易。 */
    public MerchantOffer addOffer(MerchantOffer offer) {
        this.offers.add(offer);
        this.onChange.run();
        return offer;
    }

    /** 删除指定交易。 */
    public void removeOffer(int index) {
        if (index >= 0 && index < this.offers.size()) {
            this.offers.remove(index);
            this.onChange.run();
        }
    }

    /** 更新指定交易（物品编辑后调用）。 */
    public void updateOffer(int index, MerchantOffer offer) {
        if (index >= 0 && index < this.offers.size()) {
            this.offers.set(index, offer);
            this.onChange.run();
        }
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }
}
