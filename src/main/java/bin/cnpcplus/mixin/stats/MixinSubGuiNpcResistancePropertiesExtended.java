package bin.cnpcplus.mixin.stats;

import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.client.gui.SubGuiNpcResistanceProperties;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = GuiNpcStats.class, remap = false)
public abstract class MixinSubGuiNpcResistancePropertiesExtended {
    @Shadow private DataStats stats;
    @ModifyArg(method = "func_146284_a", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/mainmenu/GuiNpcStats;setSubGui(Lnoppes/npcs/client/gui/util/SubGuiInterface;)V"), index = 0, require = 1)
    private SubGuiInterface cnpcplus$arrayPanel(SubGuiInterface screen) {
        return screen instanceof SubGuiNpcResistanceProperties ? new bin.cnpcplus.stats.SubGuiDamageResistances(stats.resistances) : screen;
    }
}
