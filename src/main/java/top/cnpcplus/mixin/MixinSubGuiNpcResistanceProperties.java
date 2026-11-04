package top.cnpcplus.mixin;

import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.client.gui.SubGuiNpcResistanceProperties;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GuiNpcStats.class, remap = false)
public abstract class MixinSubGuiNpcResistanceProperties {
    @Shadow private DataStats stats;
    @Redirect(method = "buttonEvent", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/mainmenu/GuiNpcStats;setSubGui(Lnet/minecraft/client/gui/screens/Screen;)V"), require = 1)
    private void cnpcplus$arrayEditor(GuiNpcStats parent, Screen screen) {
        parent.setSubGui(screen instanceof SubGuiNpcResistanceProperties
                ? new top.cnpcplus.stats.SubGuiDamageResistances(stats.resistances) : screen);
    }
}
