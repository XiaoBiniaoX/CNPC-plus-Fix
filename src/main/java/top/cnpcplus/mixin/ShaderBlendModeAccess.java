package top.cnpcplus.mixin;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BlendMode.class, remap = false)
public interface ShaderBlendModeAccess {
    @Accessor("lastApplied")
    static BlendMode cnpcplus$lastApplied() { throw new AssertionError(); }

    @Accessor("lastApplied")
    static void cnpcplus$lastApplied(BlendMode value) { throw new AssertionError(); }
}
