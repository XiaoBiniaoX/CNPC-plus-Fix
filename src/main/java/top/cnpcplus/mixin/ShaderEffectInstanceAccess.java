package top.cnpcplus.mixin;

import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import net.minecraft.client.renderer.EffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EffectInstance.class)
public interface ShaderEffectInstanceAccess {
    @Accessor("samplerNames")
    List<String> cnpcplus$samplerNames();

    @Accessor("samplerMap")
    Map<String, IntSupplier> cnpcplus$samplers();
}
