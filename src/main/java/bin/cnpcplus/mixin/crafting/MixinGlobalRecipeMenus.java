package bin.cnpcplus.mixin.crafting;

import bin.cnpcplus.crafting.CraftKind;
import bin.cnpcplus.crafting.network.PacketRecipeAction;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNPCGlobalMainMenu.class, remap = false)
public abstract class MixinGlobalRecipeMenus {
    @Inject(method = "func_73866_w_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$addRecipeMenus(CallbackInfo ci) {
        GuiNPCGlobalMainMenu self = (GuiNPCGlobalMainMenu) (Object) this;
        for (CraftKind kind : CraftKind.values()) {
            self.addButton(new GuiNpcButton(102 + kind.ordinal(), self.guiLeft + 285,
                    self.guiTop + 120 + 22 * kind.ordinal(), 100, 20, kind.title()));
        }
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openRecipeMenu(GuiButton button, CallbackInfo ci) {
        if (button.id < 102 || button.id > 104) return;
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketRecipeAction(PacketRecipeAction.OPEN,
                CraftKind.values()[button.id - 102], -1, new NBTTagCompound()));
        ci.cancel();
    }
}
