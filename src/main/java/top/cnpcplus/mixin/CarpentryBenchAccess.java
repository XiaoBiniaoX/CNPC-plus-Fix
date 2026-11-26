package top.cnpcplus.mixin;

import net.minecraft.core.BlockPos;
import noppes.npcs.containers.ContainerCarpentryBench;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ContainerCarpentryBench.class, remap = false)
public interface CarpentryBenchAccess {
    @Accessor(value = "pos", remap = false) BlockPos cnpcplus$pos();
}
