package top.cnpcplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {
        "noppes.npcs.client.gui.custom.GuiCreationNewParts$GuiMpmPart",
        "noppes.npcs.client.gui.custom.GuiCreationNewParts$EyesPart"
}, remap = false)
public abstract class MixinModelPartsPreviewSkin {
    @Redirect(method = {"renderModel", "m_88315_"}, at = @At(value = "FIELD",
            target = "Lnoppes/npcs/entity/EntityCustomNpc;textureLocation:Lnet/minecraft/resources/ResourceLocation;"), require = 1)
    private ResourceLocation cnpcplus$resolvePreviewSkin(EntityCustomNpc npc) {
        return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(npc).getTextureLocation(npc);
    }
}
