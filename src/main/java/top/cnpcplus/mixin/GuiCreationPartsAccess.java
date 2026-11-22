package top.cnpcplus.mixin;

import noppes.npcs.ModelData;
import noppes.npcs.client.gui.custom.GuiCreationNewParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiCreationNewParts.class, remap = false)
public interface GuiCreationPartsAccess {
    @Accessor("data") ModelData cnpcplus$modelData();
}
