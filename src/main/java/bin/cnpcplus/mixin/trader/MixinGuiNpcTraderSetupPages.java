package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.trader.TraderPager;
import bin.cnpcplus.trader.network.PacketTraderPage;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.roles.GuiNpcTraderSetup;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Admin trader setup screen: page title input, prev/next/delete buttons.
 * Button ids 110-112 and textfield 11 do not collide with vanilla ids 0-2.
 */
@Mixin(GuiNpcTraderSetup.class)
public class MixinGuiNpcTraderSetupPages {

    @Shadow(remap = false)
    private RoleTrader role;

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void cnpcplus$pageControls(CallbackInfo ci) {
        GuiNpcTraderSetup self = (GuiNpcTraderSetup) (Object) this;
        int page = TraderPager.getPage(role);
        int count = TraderPager.getPageCount(role);
        self.addLabel(new GuiNpcLabel(8, I18n.translateToLocal("cnpcplus.trader.pagetitle"), self.field_147003_i + 260, self.field_147009_r + 112));
        self.addTextField(new GuiNpcTextField(11, self, self.field_147003_i + 260, self.field_147009_r + 122, 150, 20, TraderPager.getPageTitle(role)));
        self.addLabel(new GuiNpcLabel(9, (page + 1) + "/" + count, self.field_147003_i + 340, self.field_147009_r + 150));
        self.addButton(new GuiNpcButton(110, self.field_147003_i + 214, self.field_147009_r + 184, 88, 20, I18n.translateToLocal("cnpcplus.trader.prev")));
        self.addButton(new GuiNpcButton(111, self.field_147003_i + 306, self.field_147009_r + 184, 88, 20, I18n.translateToLocal("cnpcplus.trader.next")));
        self.addButton(new GuiNpcButton(112, self.field_147003_i + 214, self.field_147009_r + 204, 180, 20, I18n.translateToLocalFormatted("cnpcplus.trader.deletepage", page + 1, count)));
    }

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcplus$saveTitle(CallbackInfo ci) {
        this.cnpcplus$applyTitle();
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$onPage(GuiButton button, CallbackInfo ci) {
        if (button.id < 110 || button.id > 112) return;
        this.cnpcplus$applyTitle();
        GuiNpcTraderSetup self = (GuiNpcTraderSetup) (Object) this;
        int page = TraderPager.getPage(role);
        int count = TraderPager.getPageCount(role);
        if (button.id == 110) {
            int target = page - 1;
            if (target < 0) {
                ci.cancel();
                return;
            }
            TraderPager.switchPage(role, target);
            CraftingViewNetwork.CHANNEL.sendToServer(new PacketTraderPage(target, false));
        } else if (button.id == 111) {
            int target = page + 1;
            if (target >= count) {
                TraderPager.addPage(role);
            }
            TraderPager.switchPage(role, target);
            CraftingViewNetwork.CHANNEL.sendToServer(new PacketTraderPage(target, false));
        } else {
            if (!TraderPager.removePage(role, page)) {
                ci.cancel();
                return;
            }
            CraftingViewNetwork.CHANNEL.sendToServer(new PacketTraderPage(page, true));
        }
        self.initGui();
        ci.cancel();
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$onUnfocused(GuiNpcTextField textField, CallbackInfo ci) {
        GuiNpcTraderSetup self = (GuiNpcTraderSetup) (Object) this;
        if (self.getTextField(11) == textField) {
            this.cnpcplus$applyTitle();
            ci.cancel();
        }
    }

    @Unique
    private void cnpcplus$applyTitle() {
        GuiNpcTraderSetup self = (GuiNpcTraderSetup) (Object) this;
        GuiNpcTextField tf = self.getTextField(11);
        if (tf != null) {
            TraderPager.setPageTitle(role, TraderPager.getPage(role), tf.getText());
        }
    }
}
