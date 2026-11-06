package bin.cnpcplus.mixin.shader;

import net.minecraft.client.util.JsonBlendingMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JsonBlendingMode.class)
public interface ShaderBlendAccess {
    @Accessor("lastApplied") static void cnpcplus$invalidate(JsonBlendingMode value) { throw new AssertionError(); }
}
