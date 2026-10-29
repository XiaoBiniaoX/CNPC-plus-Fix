package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.GuiVillagerTraderEdit;

/** 纯本地编辑页不向服务端另一菜单发送 SelectTrade。 */
@Mixin(value = MerchantScreen.class, remap = false)
public class MixinMerchantScreenLocalEdit {
    @Inject(method = "postButtonClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$localEntryClick(CallbackInfo ci) {
        if ((Object) this instanceof GuiVillagerTraderEdit screen) {
            int index = ((MerchantScreenAccessor) this).getShopItem();
            screen.getMenu().setSelectionHint(index);
            screen.getMenu().tryMoveItems(index);
            ci.cancel();
        }
    }
}
