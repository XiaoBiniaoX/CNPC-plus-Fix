package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.trader.TraderPager;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTrader;
import noppes.npcs.shared.client.gui.components.GuiBasicContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GuiBasicContainer.class, remap = false)
public class MixinGuiBasicContainerTitle {

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/language/I18n;get(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private String cnpcplus$replaceTitle(String key, Object[] args) {
        if ("role.trader".equals(key)) {
            GuiBasicContainer self = (GuiBasicContainer) (Object) this;
            if (self instanceof GuiContainerNPCInterface) {
                EntityNPCInterface npc = ((GuiContainerNPCInterface) self).npc;
                if (npc != null && npc.role instanceof RoleTrader) {
                    String t = TraderPager.getPageTitle((RoleTrader) npc.role);
                    if (!t.isEmpty()) {
                        return t;
                    }
                }
            }
        }
        return I18n.get(key, args);
    }
}