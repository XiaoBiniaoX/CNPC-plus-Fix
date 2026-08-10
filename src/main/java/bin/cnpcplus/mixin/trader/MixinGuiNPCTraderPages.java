package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.trader.TraderPager;
import bin.cnpcplus.trader.network.PacketTraderPage;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.client.gui.player.GuiNPCTrader;
import noppes.npcs.roles.RoleTrader;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNPCTrader.class, remap = false)
public class MixinGuiNPCTraderPages {

    @Shadow(remap = false)
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
        PacketDistributor.sendToServer(new PacketTraderPage(target, false));
        ((GuiNPCTrader) (Object) this).init();
        ci.cancel();
    }
}
