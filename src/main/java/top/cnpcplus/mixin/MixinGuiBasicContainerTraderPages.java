package top.cnpcplus.mixin;

import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.shared.client.gui.components.GuiBasicContainer;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.trader.TraderPager;

@Mixin(value = GuiBasicContainer.class, remap = false)
public class MixinGuiBasicContainerTraderPages {

    @Inject(method = "m_7856_", at = @At("TAIL"))
    private void cnpcplus$pageButtons(CallbackInfo ci) {
        AbstractContainerScreenAccess access = (AbstractContainerScreenAccess) (Object) this;
        if (!(access.cnpcplus$getMenu() instanceof ContainerNPCTrader trader)) return;
        GuiBasicContainer self = (GuiBasicContainer) (Object) this;
        int page = TraderPager.getPage(trader.role);
        int count = TraderPager.getPageCount(trader.role);
        int l = access.cnpcplus$getLeftPos();
        int t = access.cnpcplus$getTopPos();
        if (count > 1) {
            self.addButton(new GuiButtonNop((IGuiInterface) self, 100, l + 10, t + 140, 20, 20, "<"));
            self.addButton(new GuiButtonNop((IGuiInterface) self, 101, l + 195, t + 140, 20, 20, ">"));
        }
        self.addLabel(new GuiLabel(200, (page + 1) + "/" + count, l + 196, t - 12));
    }
}
