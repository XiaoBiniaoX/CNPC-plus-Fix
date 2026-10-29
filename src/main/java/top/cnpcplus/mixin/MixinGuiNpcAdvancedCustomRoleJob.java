package top.cnpcplus.mixin;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import noppes.npcs.roles.RoleInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.GuiVillagerTraderEdit;
import top.cnpcplus.roles.RoleVillagerTrader;

/**
 * 扩展高级设置面板的身份/职业下拉数组 + 路由村民交易商编辑面板。
 */
@Mixin(value = GuiNpcAdvanced.class, remap = false)
public class MixinGuiNpcAdvancedCustomRoleJob {

    private static final String BI_DIR_CTOR =
        "Lnoppes/npcs/shared/client/gui/components/GuiButtonBiDirectional;<init>(Lnoppes/npcs/shared/client/gui/listeners/IGuiInterface;IIIII[Ljava/lang/String;I)V";

    @ModifyArg(
        method = "m_7856_",
        at = @At(value = "INVOKE", target = BI_DIR_CTOR, ordinal = 0),
        index = 6
    )
    private String[] cnpcplus$extendRoleArray(String[] arr) {
        String[] n = Arrays.copyOf(arr, arr.length + 1);
        n[arr.length] = "role.villagertrader";
        return n;
    }

    @ModifyArg(
        method = "m_7856_",
        at = @At(value = "INVOKE", target = BI_DIR_CTOR, ordinal = 1),
        index = 6
    )
    private String[] cnpcplus$extendJobArray(String[] arr) {
        String[] n = Arrays.copyOf(arr, arr.length + 1);
        n[arr.length] = "job.testprofession";
        return n;
    }

    /** setGuiData 的 RoleData 分支：type==8 时打开编辑面板（基于原版村民交易界面）。 */
    @Inject(method = "setGuiData", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$openVillagerTraderEdit(CompoundTag compound, CallbackInfo ci) {
        if (compound.getBoolean("RoleData")) {
            GuiNpcAdvanced self = (GuiNpcAdvanced) (Object) this;
            self.npc.role.load(compound);
            if (self.npc.role instanceof RoleVillagerTrader role) {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    // 从 role 加载实际交易数据
                    var trades = role.getTrades();
                    var offers = new net.minecraft.world.item.trading.MerchantOffers();
                    for (var t : trades) {
                        offers.add(new net.minecraft.world.item.trading.MerchantOffer(
                            t.inputA.copy(), t.inputB.copy(), t.output.copy(),
                            t.maxUses, t.xp, t.priceMult
                        ));
                    }
                    var merchant = new top.cnpcplus.villagertrade.EditableMerchant(offers, () -> {});
                    merchant.setTradingPlayer(player);
                    var menu = new top.cnpcplus.villagertrade.EditableMerchantMenu(
                        0, player.getInventory(), merchant, () -> {}
                    );
                    var screen = new top.cnpcplus.client.gui.GuiVillagerTraderEdit(
                        self.npc, menu, player.getInventory(),
                        net.minecraft.network.chat.Component.translatable("cnpcplus.villagertrader.edit"),
                        merchant
                    );
                    Minecraft.getInstance().setScreen(screen);
                    ci.cancel();
                }
            }
        }
    }
}
