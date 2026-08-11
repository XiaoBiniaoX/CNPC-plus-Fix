package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.trader.TraderPager;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replace the window title for trader screens. 1.20.1 intercepts the
 * I18n call with the key "role.trader"; 1.12.2 does the same inside
 * GuiContainerNPCInterface.func_146976_a, falling back to the page
 * title, then to the vanilla translation.
 */
@Mixin(GuiContainerNPCInterface.class)
public class MixinGuiContainerNPCInterfaceTitle {

    @Redirect(method = "func_146976_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/text/translation/I18n;func_74838_a(Ljava/lang/String;)Ljava/lang/String;"), remap = false)
    private String cnpcplus$pageTitle(String key) {
        if (!"role.trader".equals(key)) return I18n.translateToLocal(key);
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        if (base.npc != null && base.npc.roleInterface instanceof RoleTrader) {
            String title = TraderPager.getPageTitle((RoleTrader) base.npc.roleInterface);
            if (title != null && !title.isEmpty()) return title;
        }
        return I18n.translateToLocal(key);
    }
}