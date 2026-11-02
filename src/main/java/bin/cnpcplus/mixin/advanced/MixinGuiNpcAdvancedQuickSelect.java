package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.client.gui.SubGuiRoleJobSelect;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcAdvanced.class, remap = false)
public abstract class MixinGuiNpcAdvancedQuickSelect extends GuiNPCInterface2 {
    @Shadow protected abstract void func_146284_a(GuiButton button);
    protected MixinGuiNpcAdvancedQuickSelect(EntityNPCInterface npc) { super(npc); }
    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$buttons(CallbackInfo ci) {
        for (int id : new int[]{8, 5}) {
            GuiNpcButton button = getButton(id);
            addButton(new GuiNpcButton(720 + id, button.x - 70, button.y, 66, 20, "cnpcplus.select.quick"));
        }
    }
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$select(GuiButton button, CallbackInfo ci) {
        if (getSubGui() instanceof SubGuiRoleJobSelect) { getSubGui().buttonEvent(button); ci.cancel(); return; }
        if (button.id != 728 && button.id != 725) return;
        int id = button.id - 720;
        setSubGui(new SubGuiRoleJobSelect(getButton(id), id == 8, value -> {
            GuiNpcButton original = getButton(id);
            if (value != original.getValue()) { original.setDisplay(value); func_146284_a(original); }
        })); ci.cancel();
    }
}
