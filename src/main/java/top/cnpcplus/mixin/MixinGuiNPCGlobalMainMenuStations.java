package top.cnpcplus.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.station.StationPacket;
import top.cnpcplus.station.StationRecipes;

@Mixin(value = GuiNPCGlobalMainMenu.class, remap = false)
public class MixinGuiNPCGlobalMainMenuStations {
    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void cnpcplus$buttons(CallbackInfo ci) {
        var self = (GuiNPCGlobalMainMenu)(Object)this;
        for (int i = 0; i < 3; i++) self.addButton(new GuiButtonNop(self, 105 + i,
                self.guiLeft + 285, self.guiTop + 10 + 22 * i, 100, 20, "cnpcplus.station." + StationRecipes.KINDS[i]));
    }
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$open(GuiButtonNop button, CallbackInfo ci) {
        if (button.id < 105 || button.id > 107) return;
        PacketDistributor.sendToServer(new StationPacket(0, button.id - 105, new net.minecraft.nbt.CompoundTag()));
        ci.cancel();
    }
}
