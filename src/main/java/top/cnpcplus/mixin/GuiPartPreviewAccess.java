package top.cnpcplus.mixin;

import noppes.npcs.client.parts.MpmPartData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "noppes.npcs.client.gui.custom.GuiCreationNewParts$GuiMpmPart", remap = false)
public interface GuiPartPreviewAccess {
    @Accessor("data") void cnpcplus$partData(MpmPartData data);
}
