package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.smelting.network.PacketSmeltingAction;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the smelting editor button to CNPC's global settings screen.
 *
 * This button went missing twice while developing the 1.20.1 and 1.21.1 versions.
 * Both causes are guarded against here:
 *
 *  1. A non-private static field made Mixin refuse the whole class
 *     (InvalidMixinException: contains non-private static field), so no button.
 *     BUTTON_ID is therefore private static final.
 *
 *  2. The injection named a method that did not exist in that version. With
 *     "injectors": { "defaultRequire": 0 } in the config a miss is silent, so the
 *     button simply never appeared. Both injections below set require = 1, which
 *     turns a naming mistake into a startup failure instead of a mystery.
 *
 * Method names verified against the shipped CustomNPCs jar: initGui is
 * func_73866_w_ and actionPerformed is func_146284_a(GuiButton).
 *
 * The injection must be at TAIL: initGui clears the button list at its start
 * (GuiContainerNPCInterface L101-107), so anything added at HEAD is discarded.
 *
 * Coordinates continue the vanilla column, which runs at guiLeft + 85 with a
 * 22px step from guiTop + 10; the last entry (global.linked) sits at +186. The
 * new button goes to the right of that column, matching 1.20.1 and 1.21.1.
 */
@Mixin(value = GuiNPCGlobalMainMenu.class, remap = false)
public class MixinGuiNPCGlobalMainMenuSmelting {
    private static final int BUTTON_ID = 101;
    private static final int BUTTON_X = 285;
    private static final int BUTTON_Y = 10 + 22 * 8;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$addSmeltingButton(CallbackInfo ci) {
        GuiNPCGlobalMainMenu self = (GuiNPCGlobalMainMenu) (Object) this;
        self.addButton(new GuiNpcButton(BUTTON_ID,
                self.guiLeft + BUTTON_X, self.guiTop + BUTTON_Y,
                BUTTON_WIDTH, BUTTON_HEIGHT, "cnpcplus.smelting.open"));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), remap = false,
            cancellable = true, require = 1)
    private void cnpcplus$handleSmeltingButton(GuiButton guibutton, CallbackInfo ci) {
        if (guibutton == null || guibutton.id != BUTTON_ID) {
            return;
        }
        // The server opens the editor, so permission is enforced there.
        CraftingViewNetwork.CHANNEL.sendToServer(
                new PacketSmeltingAction(PacketSmeltingAction.ACTION_OPEN, -1));
        ci.cancel();
    }
}
