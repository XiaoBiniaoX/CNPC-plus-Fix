package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.client.gui.GuiChoiceButton;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcStats.class, remap = false)
public abstract class MixinGuiNpcStatsChoices extends GuiNPCInterface2 {
    @Shadow protected abstract void func_146284_a(GuiButton button);
    protected MixinGuiNpcStatsChoices(EntityNPCInterface npc) { super(npc); }

    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$choiceButtons(CallbackInfo ci) {
        for (int id : new int[]{4, 5, 6, 7, 17, 22}) {
            GuiNpcButton original = getButton(id);
            if (original != null) GuiChoiceButton.replace(this, original, original.getWidth());
        }
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$choiceClick(GuiButton button, CallbackInfo ci) {
        if (!(button instanceof GuiChoiceButton)) return;
        GuiChoiceButton choice = (GuiChoiceButton) button;
        choice.original().setDisplay(choice.value());
        func_146284_a(choice.original());
        ci.cancel();
    }
}
