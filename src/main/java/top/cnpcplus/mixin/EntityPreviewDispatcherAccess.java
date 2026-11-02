package top.cnpcplus.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EntityRenderDispatcher.class, remap = false)
public interface EntityPreviewDispatcherAccess {
    @Accessor("shouldRenderShadow") boolean cnpcplus$renderShadow();
}
