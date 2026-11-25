package top.cnpcplus.mixin;

import noppes.npcs.client.gui.select.GuiTextureSelection;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiTextureSelection.class, remap = false)
public abstract class MixinTextureSelectionDefaults {
    @Shadow private String location;
    @Shadow private String selectedDomain;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void cnpcplus$defaultDirectory(EntityNPCInterface npc, String texture, int type, CallbackInfo ci) {
        if ((texture == null || texture.isEmpty()) && (type == 1 || type == 2)) {
            selectedDomain = "customnpcs";
            location = type == 1 ? "textures/cloak/" : "textures/overlays/";
            // Browse a directory, do not pretend a directory is a selected/savable texture.
        }
    }
}
