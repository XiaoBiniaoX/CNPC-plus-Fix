package bin.cnpcplus.mixin.shader;

import java.util.List;
import net.minecraft.client.shader.ShaderManager;
import net.minecraft.client.shader.ShaderUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderManager.class)
public interface ShaderProgramAccess {
    @Accessor("shaderUniforms") List<ShaderUniform> cnpcplus$uniforms();
    @Accessor("currentProgram") static void cnpcplus$program(int program) { throw new AssertionError(); }
    @Accessor("staticShaderManager") static ShaderManager cnpcplus$manager() { throw new AssertionError(); }
    @Accessor("staticShaderManager") static void cnpcplus$manager(ShaderManager manager) { throw new AssertionError(); }
}
