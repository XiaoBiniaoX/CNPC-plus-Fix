package bin.cnpcplus.mixin.shader;

import bin.cnpcplus.shader.ShaderController;
import bin.cnpcplus.shader.client.ClientShaders;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinRenderTileTarget {
    @Redirect(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V"))
    private void cnpcplus$target(TileEntitySpecialRenderer<TileEntity> renderer, TileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        ClientShaders.pushTarget(tile.getWorld() == null ? "" : ShaderController.blockTarget(tile.getWorld(), tile.getPos()));
        try { renderer.render(tile, x, y, z, partial, destroy, alpha); } finally { ClientShaders.popTarget(); }
    }
}
