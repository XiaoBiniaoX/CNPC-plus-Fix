package bin.cnpcplus.mixin.mount;

import bin.cnpcplus.common.IMountControlData;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNpcAI;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * High-version style mount control toggle in the AI screen.
 * Button id 16 and label id 25 are unused by the vanilla screen.
 * GuiNpcAI.ai is private on a noppes class, so npc.ais is used instead of
 * @Shadow (see findings.md); guiLeft/guiTop/npc are public on the base screen.
 */
@Mixin(value = GuiNpcAI.class, remap = false)
public abstract class MixinGuiNpcAIMountControl {
    private static final int BUTTON_ID = 16;

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addMountControlButton(CallbackInfo ci) {
        GuiNpcAI gui = (GuiNpcAI) (Object) this;
        if (gui.npc == null || gui.npc.ais == null) {
            return;
        }
        boolean enabled = ((IMountControlData) (Object) gui.npc.ais).cnpcplus$getMountControl();
        gui.addLabel(new GuiNpcLabel(25, "ai.mountcontrol", gui.guiLeft + 150, gui.guiTop + 190));
        gui.addButton(new GuiNpcButton(BUTTON_ID, gui.guiLeft + 231, gui.guiTop + 185, 60, 20,
                new String[]{"gui.no", "gui.yes"}, enabled ? 1 : 0));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$handleMountControl(GuiButton button, CallbackInfo ci) {
        if (!(button instanceof GuiNpcButton) || button.id != BUTTON_ID) {
            return;
        }
        GuiNpcAI gui = (GuiNpcAI) (Object) this;
        if (gui.npc == null || gui.npc.ais == null) {
            return;
        }
        ((IMountControlData) (Object) gui.npc.ais)
                .cnpcplus$setMountControl(((GuiNpcButton) button).getValue() == 1);
        ci.cancel();
    }
}
