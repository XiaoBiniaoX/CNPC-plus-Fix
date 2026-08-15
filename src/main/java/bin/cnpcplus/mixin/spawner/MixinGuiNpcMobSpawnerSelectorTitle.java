package bin.cnpcplus.mixin.spawner;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.gui.GuiNpcMobSpawnerSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuiNpcMobSpawnerSelector.class, remap = false)
public class MixinGuiNpcMobSpawnerSelectorTitle {
    @Inject(method = "getCompound", at = @At("RETURN"), remap = false)
    private void cnpcplus$preserveCloneName(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound compound = cir.getReturnValue();
        GuiNpcMobSpawnerSelector self = (GuiNpcMobSpawnerSelector) (Object) this;
        String selected = self.getSelected();
        if (compound != null && selected != null && !selected.isEmpty()) {
            compound.setString("ClonedName", selected);
        }
    }
}
