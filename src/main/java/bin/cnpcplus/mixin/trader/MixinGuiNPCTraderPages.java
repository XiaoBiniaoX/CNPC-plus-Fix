package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.trader.TraderPager;
import bin.cnpcplus.trader.network.PacketTraderPage;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.player.GuiNPCTrader;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player buy screen paging. GuiNPCTrader does not declare func_73866_w_ /
 * func_146284_a (they live in GuiContainerNPCInterface), so injection goes
 * into the base class and guards with instanceof; the role is read from
 * npc.roleInterface instead of @Shadow.
 */
@Mixin(GuiContainerNPCInterface.class)
public class MixinGuiNPCTraderPages {

    private boolean cnpcplus$isTrader() {
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        return (Object) base instanceof GuiNPCTrader
                && base.npc != null && base.npc.roleInterface instanceof RoleTrader;
    }

    private RoleTrader cnpcplus$role() {
        return (RoleTrader) ((GuiContainerNPCInterface) (Object) this).npc.roleInterface;
    }

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void cnpcplus$pageButtons(CallbackInfo ci) {
        if (!cnpcplus$isTrader()) return;
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        RoleTrader role = cnpcplus$role();
        int page = TraderPager.getPage(role);
        int count = TraderPager.getPageCount(role);
        if (count > 1) {
            base.addButton(new GuiNpcButton(100, base.field_147003_i + 10, base.field_147009_r + 140, 20, 20, "<"));
            base.addButton(new GuiNpcButton(101, base.field_147003_i + 195, base.field_147009_r + 140, 20, 20, ">"));
        }
        base.addLabel(new GuiNpcLabel(200, (page + 1) + "/" + count, base.field_147003_i + 196, base.field_147009_r - 12));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$onPage(GuiButton button, CallbackInfo ci) {
        if (!cnpcplus$isTrader()) return;
        if (button.id != 100 && button.id != 101) return;
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        RoleTrader role = cnpcplus$role();
        int page = TraderPager.getPage(role);
        int count = TraderPager.getPageCount(role);
        int target = button.id == 100 ? page - 1 : page + 1;
        if (target < 0 || target >= count) {
            ci.cancel();
            return;
        }
        TraderPager.switchPage(role, target);
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketTraderPage(target, false));
        base.initGui();
        ci.cancel();
    }
}