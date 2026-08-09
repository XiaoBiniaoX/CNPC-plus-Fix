package top.cnpcplus.mixin;

import noppes.npcs.client.gui.player.GuiNPCTrader;
import noppes.npcs.roles.RoleTrader;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.trader.TraderPager;
import top.cnpcplus.trader.network.PacketTraderPage;
import top.cnpcplus.trader.network.TraderPagePacketHandler;

@Mixin(value = GuiNPCTrader.class, remap = false)
public class MixinGuiNPCTraderPages {

    @Shadow
    private RoleTrader role;

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onPage(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 100 && button.id != 101) return;
        int page = TraderPager.getPage(this.role);
        int count = TraderPager.getPageCount(this.role);
        int target = button.id == 100 ? page - 1 : page + 1;
        if (target < 0 || target >= count) {
            ci.cancel();
            return;
        }
        TraderPager.switchPage(this.role, target);
        TraderPagePacketHandler.CHANNEL.sendToServer(new PacketTraderPage(target, false));
        ((GuiNPCTrader) (Object) this).m_7856_();
        ci.cancel();
    }
}
