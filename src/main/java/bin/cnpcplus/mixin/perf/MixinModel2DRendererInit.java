package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.perf.client.Model2DOutlineCache;
import bin.cnpcplus.perf.client.RenderResourceCaches;
import bin.cnpcplus.perf.client.WeakInvalidationRegistry;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.shared.client.model.Model2DRenderer;
import noppes.npcs.shared.client.model.NopModelPart;
import noppes.npcs.shared.client.model.util.Polygon;
import noppes.npcs.shared.common.util.NopVector2i;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 缓存命中免计算；未命中完整执行 CNPC 原版 init，保留缺图、越界 UV 等语义。
 *
 * <p>1.21.1 移植说明：1.20.1 版 {@code Model2DRenderer} 有一个 {@code VertexBuffer cache}
 * 字段（VBO 缓存），1.21.1.20251230 的 CNPC 已移除该字段 —— 因此本类不再 @Shadow 它，
 * {@code cnpcplus$resetRenderCache} 只需清空 {@code compiled} 实例 Map。
 */
@Mixin(value = Model2DRenderer.class, remap = false)
public abstract class MixinModel2DRendererInit implements WeakInvalidationRegistry.Resettable {
    @Shadow @Final private int width;
    @Shadow @Final private int height;
    @Shadow @Final private NopVector2i texPos;
    @Shadow @Final private float x1;
    @Shadow @Final private float x2;
    @Shadow @Final private float y1;
    @Shadow @Final private float y2;
    @Shadow @Final private Map<ResourceLocation, Polygon[]> compiled;

    @Unique
    private Model2DOutlineCache.Key cnpcplus$key(ResourceLocation location) {
        NopModelPart part = (NopModelPart)(Object)this;
        return new Model2DOutlineCache.Key(location, texPos.x, texPos.y, width, height,
                part.xTexSize, part.yTexSize, x1, x2, y1, y2);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cnpcplus$register(CallbackInfo ci) {
        RenderResourceCaches.register(this);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$lookup(ResourceLocation location, CallbackInfoReturnable<Polygon[]> cir) {
        if (!RenderResourceCaches.outlineCacheEnabled || location == null) return;
        Polygon[] value = Model2DOutlineCache.get(cnpcplus$key(location));
        if (value != null) cir.setReturnValue(value);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void cnpcplus$remember(ResourceLocation location, CallbackInfoReturnable<Polygon[]> cir) {
        if (!RenderResourceCaches.outlineCacheEnabled || location == null) return;
        Model2DOutlineCache.put(cnpcplus$key(location), cir.getReturnValue());
        // 全局 LRU 管理生命周期，避免实例 Map 把已淘汰的数据继续强引用。
        compiled.clear();
    }

    @Redirect(method = "init", at = @At(value = "INVOKE",
            target = "Ljavax/imageio/ImageIO;read(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage cnpcplus$closeStream(InputStream stream) throws IOException {
        try (InputStream input = stream) { return ImageIO.read(input); }
    }

    @Override
    public void cnpcplus$resetRenderCache() {
        compiled.clear();
    }
}
