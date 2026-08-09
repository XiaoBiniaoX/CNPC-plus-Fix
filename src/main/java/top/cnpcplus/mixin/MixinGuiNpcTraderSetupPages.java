package top.cnpcplus.mixin;

import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.roles.GuiNpcTraderSetup;
import noppes.npcs.roles.RoleTrader;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.trader.TraderPager;
import top.cnpcplus.trader.network.PacketTraderPage;
import top.cnpcplus.trader.network.TraderPagePacketHandler;

@Mixin(value = GuiNpcTraderSetup.class, remap = false)
public class MixinGuiNpcTraderSetupPages {

    @Shadow
    private RoleTrader role;

    @Inject(method = "m_7856_", at = @At("TAIL"))
    private void cnpcplus$pageControls(CallbackInfo ci) {
        GuiNpcTraderSetup self = (GuiNpcTraderSetup) (Object) this;
        int page = TraderPager.getPage(this.role);
        int count = TraderPager.getPageCount(this.role);
        int l = self.guiLeft;
        int t = self.guiTop;
        self.addLabel(new GuiLabel(8, "页面标题", l + 260, t + 112));
        self.addTextField(new GuiTextFieldNop(11, (Screen) self, l + 260, t + 122, 150, 20, TraderPager.getPageTitle(this.role)));
        self.addLabel(new GuiLabel(9, (page + 1) + "/" + count, l + 340, t + 150));
        self.addButton(new GuiButtonNop((IGuiInterface) self, 110, l + 214, t + 184, 88, 20, "上一页"));
        self.addButton(new GuiButtonNop((IGuiInterface) self, 111, l + 306, t + 184, 88, 20, "下一页"));
        self.addButton(new GuiButtonNop((IGuiInterface) self, 112, l + 214, t + 204, 180, 20, "删除本页(" + (page + 1) + "/" + count + ")"));
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void cnpcplus$saveTitle(CallbackInfo ci) {
        this.cnpcplus$applyTitle();
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onPage(GuiButtonNop button, CallbackInfo ci) {
        if (button.id < 110 || button.id > 112) return;
        this.cnpcplus$applyTitle();
        int page = TraderPager.getPage(this.role);
        int count = TraderPager.getPageCount(this.role);
        if (button.id == 110) {
            int target = page - 1;
            if (target < 0) {
                ci.cancel();
                return;
            }
            TraderPager.switchPage(this.role, target);
            TraderPagePacketHandler.CHANNEL.sendToServer(new PacketTraderPage(target, false));
        } else if (button.id == 111) {
            int target = page + 1;
            if (target >= count) {
                TraderPager.addPage(this.role);
            }
            TraderPager.switchPage(this.role, target);
            TraderPagePacketHandler.CHANNEL.sendToServer(new PacketTraderPage(target, false));
        } else {
            if (!TraderPager.removePage(this.role, page)) {
                ci.cancel();
                return;
            }
            TraderPagePacketHandler.CHANNEL.sendToServer(new PacketTraderPage(page, true));
        }
        ((GuiNpcTraderSetup) (Object) this).m_7856_();
        ci.cancel();
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onUnfocused(GuiTextFieldNop textField, CallbackInfo ci) {
        GuiNpcTraderSetup self = (GuiNpcTraderSetup) (Object) this;
        if (textField.id == 11) {
            this.cnpcplus$applyTitle();
            ci.cancel();
        }
    }

    private void cnpcplus$applyTitle() {
        GuiNpcTraderSetup self = (GuiNpcTraderSetup) (Object) this;
        GuiTextFieldNop tf = self.getTextField(11);
        if (tf != null) {
            int page = TraderPager.getPage(this.role);
            String value = tf.getValue();
            TraderPager.setPageTitle(this.role, page, value);
            org.slf4j.LoggerFactory.getLogger("cnpcplus").warn(
                    "[trader] title page={} value={}", page, value);
        }
    }
}
