package top.cnpcplus.mixin;

import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PostChain.class, remap = false)
public interface ShaderPostChainAccess {
    @Accessor("passes") List<PostPass> cnpcplus$passes();
}
