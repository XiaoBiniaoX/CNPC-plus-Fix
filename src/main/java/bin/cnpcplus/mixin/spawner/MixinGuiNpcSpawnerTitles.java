package bin.cnpcplus.mixin.spawner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.client.resources.I18n;
import noppes.npcs.client.gui.roles.GuiNpcSpawner;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcSpawner.class, remap = false)
public class MixinGuiNpcSpawnerTitles {
    @Inject(method = "setGuiData", at = @At("TAIL"), remap = false)
    private void cnpcplus$updateSummonButtonTitles(NBTTagCompound compound, CallbackInfo ci) {
        GuiNpcSpawner self = (GuiNpcSpawner) (Object) this;
        String[] titles = {self.title1, self.title2, self.title3, self.title4, self.title5, self.title6};
        for (int slot = 0; slot < titles.length; slot++) {
            GuiButton button = self.getButton(slot);
            if (button != null) button.displayString = I18n.format(titles[slot]);
        }
    }

    @Inject(method = "func_146284_a", at = @At("TAIL"), remap = false)
    private void cnpcplus$clearButtonOnRemove(GuiButton guibutton, CallbackInfo ci) {
        if (!(guibutton instanceof GuiNpcButton)) return;
        int id = ((GuiNpcButton) guibutton).field_146127_k;
        if (id < 20 || id > 25) return;
        GuiNpcSpawner self = (GuiNpcSpawner) (Object) this;
        GuiButton target = self.getButton(id - 20);
        if (target != null) target.displayString = I18n.format("gui.selectnpc");
    }
}
