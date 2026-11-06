package bin.cnpcplus.mixin.shader;

import net.minecraft.client.shader.ShaderUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderUniform.class)
public interface ShaderUniformAccess {
    @Accessor("uniformCount") int cnpcplus$count();
    @Accessor("uniformType") int cnpcplus$type();
}
