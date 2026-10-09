package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.perf.client.BlueprintPreviewCache;
import noppes.npcs.blocks.tiles.TileBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 相同名字/尺寸的蓝图再次下发也必须更新。只递增版本，不在网络线程操作 GL。 */
@Mixin(value = TileBuilder.class, remap = false)
public abstract class MixinTileBuilderPreviewInvalidation {
    @Inject(method = "setDrawSchematic", at = @At("RETURN"))
    private void cnpcplus$changed(CallbackInfo ci) { BlueprintPreviewCache.changed(); }
}
