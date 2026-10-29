package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MerchantMenu.class, remap = false)
public interface MerchantMenuAccessor {
    @Accessor("tradeContainer")
    MerchantContainer getTradeContainer();

    /** 阶段225：@Shadow trader 被 AP 静默跳过 refmap → 改走已验证的 @Accessor 路线取商人。 */
    @Accessor("trader")
    Merchant getTrader();
}
