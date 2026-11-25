package top.cnpcplus.mixin;

import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.SubGuiNpcMovement;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.GuiSelectionButton;
import top.cnpcplus.client.gui.SubGuiNpcOptionSelect;

@Mixin(value = SubGuiNpcMovement.class, remap = false)
public abstract class MixinSubGuiNpcMovementChoices {
    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$choices(CallbackInfo ci) {
        SubGuiNpcMovement self = (SubGuiNpcMovement)(Object)this;
        for (int id : new int[]{0, 15}) GuiSelectionButton.add(self, self.getButton(id), 172);
        for (int id : new int[]{3, 12}) {
            GuiButtonNop original = self.getButton(id);
            if (original == null) continue; // Standing and walking animation buttons are mutually exclusive.
            original.visible = false;
            original.shown = false;
            self.addButton(new GuiButtonNop(self, id, original.getX(), original.getY(), original.getWidth(), original.getHeight(),
                    original.getMessage().getString() + "…", pressed -> self.setSubGui(new SubGuiNpcOptionSelect(self, original,
                    "cnpcplus.select.animations", NoppesUtil.getLastNpc(),
                    id == 3 ? new int[]{0, 1, 2, 3, 4, 5, 6, 7} : new int[]{0, 4, 6, 5, 7, 3}))));
        }
    }
}
