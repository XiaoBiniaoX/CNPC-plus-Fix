package top.cnpcplus.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNpcAI;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.GuiSelectionButton;

@Mixin(value = {GuiNpcAI.class, GuiNpcStats.class}, remap = false)
public abstract class MixinNpcEditorSelections {
    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$explicitChoices(CallbackInfo ci) {
        GuiNPCInterface2 self = (GuiNPCInterface2)(Object)this;
        int[] ids = (Object)this instanceof GuiNpcAI ? new int[]{5, 6, 7, 10, 15, 16, 23} : new int[]{4, 5, 6, 7, 17, 22};
        for (int id : ids) {
            var button = self.getButton(id);
            GuiSelectionButton.add(self, button, button.getWidth());
        }
    }
}
