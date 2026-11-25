package bin.cnpcplus.mixin.villagertrade;

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
        method = "init",
        at = @At(value = "INVOKE", target = BI_DIR_CTOR, ordinal = 0),
        index = 6
    )
    private String[] cnpcplus$extendRoleArray(String[] arr) {
        String[] n = Arrays.copyOf(arr, arr.length + 1);
        n[arr.length] = "role.villagertrader";
        return n;
    }

    @ModifyArg(
        method = "init",
        at = @At(value = "INVOKE", target = BI_DIR_CTOR, ordinal = 1),
        index = 6
    )
    private String[] cnpcplus$extendJobArray(String[] arr) {
        String[] n = Arrays.copyOf(arr, arr.length + 1);
        n[arr.length] = "job.testprofession";
        return n;
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void cnpcplus$quickSelect(CallbackInfo ci) {
        GuiNpcAdvanced self = (GuiNpcAdvanced)(Object)this;
        var font = Minecraft.getInstance().font;
        int quickWidth = Math.max(44, font.width(net.minecraft.client.resources.language.I18n.get("cnpcplus.select.quick")) + 12);
        int labelWidth = Math.min(120, Math.max(60, Math.max(font.width(self.getButton(8).getMessage()), font.width(self.getButton(5).getMessage()))));
        for (int id : new int[]{8, 5}) {
            var original = self.getButton(id);
            if (original == null) continue;
            original.visible = false;
            original.shown = false;
            var edit = self.getButton(id == 8 ? 3 : 4);
            int rowWidth = quickWidth + 6 + labelWidth + 6 + edit.getWidth();
            int x = self.guiLeft + (self.imageWidth - rowWidth) / 2;
            String current = original.getMessage().getString();
            String label = font.width(current) <= labelWidth ? current : font.plainSubstrByWidth(current, labelWidth - font.width("…")) + "…";
            var valueLabel = new noppes.npcs.shared.client.gui.components.GuiLabel(720 + id,
                    label, x + quickWidth + 6, original.getY() + 6, current);
            valueLabel.setWidth(labelWidth);
            valueLabel.setCentered(true);
            self.addLabel(valueLabel);
            self.addButton(new noppes.npcs.shared.client.gui.components.GuiButtonNop(self, 720 + id,
                    x, original.getY(), quickWidth, 20, "cnpcplus.select.quick",
                    pressed -> self.setSubGui(new top.cnpcplus.client.gui.SubGuiRoleJobSelect(self, id))));
            edit.setX(x + quickWidth + 6 + labelWidth + 6);
        }
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
                        offers.add(t.toOffer());
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
