package top.cnpcplus.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
    @Accessor("shopItem")
    int getShopItem();

    @Accessor("shopItem")
    void setShopItem(int value);

    @Accessor("scrollOff")
    int getScrollOff();

    @Accessor("scrollOff")
    void setScrollOff(int value);
}
