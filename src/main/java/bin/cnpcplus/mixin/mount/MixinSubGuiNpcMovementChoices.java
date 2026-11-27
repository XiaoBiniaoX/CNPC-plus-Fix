package bin.cnpcplus.mixin.mount;

import bin.cnpcplus.client.gui.GuiChoiceButton;
import bin.cnpcplus.client.gui.SubGuiNpcOptionSelect;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.SubGuiNpcMovement;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcMovement.class, remap = false)
public abstract class MixinSubGuiNpcMovementChoices extends SubGuiInterface {
    @Shadow private DataAI ai;
    @Shadow protected abstract void func_146284_a(GuiButton button);

    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$movementChoices(CallbackInfo ci) {
        for (int id : new int[]{0, 15}) {
            GuiNpcButton original = getButton(id);
            if (original != null) GuiChoiceButton.replace(this, original, 172);
        }
        addAnimationPicker(3, new int[]{0, 1, 2, 3, 4, 5, 6, 7});
        addAnimationPicker(12, new int[]{0, 4, 6, 5, 7, 3});
    }

    private void addAnimationPicker(int id, int[] animations) {
        GuiNpcButton original = getButton(id);
        if (original == null) return;
        original.setVisible(false);
        addButton(new GuiNpcButton(31000 + id, original.x, original.y, original.getWidth(), original.height,
                original.displayString + "…"));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$movementClick(GuiButton button, CallbackInfo ci) {
        if (button instanceof GuiChoiceButton) {
            GuiChoiceButton choice = (GuiChoiceButton) button;
            choice.original().setDisplay(choice.value());
            func_146284_a(choice.original());
            ci.cancel();
            return;
        }
        if (button.id != 31003 && button.id != 31012) return;
        int id = button.id - 31000;
        GuiNpcButton original = getButton(id);
        int[] animations = id == 3 ? new int[]{0, 1, 2, 3, 4, 5, 6, 7} : new int[]{0, 4, 6, 5, 7, 3};
        setSubGui(new SubGuiNpcOptionSelect(original, "cnpcplus.select.animation", npc, animations, value -> {
            GuiNpcButton current = getButton(id);
            current.setDisplay(value);
            func_146284_a(current);
            func_73866_w_();
        }));
        ci.cancel();
    }
}
