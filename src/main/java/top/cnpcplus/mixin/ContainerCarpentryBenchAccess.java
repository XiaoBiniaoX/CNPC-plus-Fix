package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import noppes.npcs.containers.ContainerCarpentryBench;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ContainerCarpentryBench.class)
public interface ContainerCarpentryBenchAccess {

    @Accessor(value = "player", remap = false)
    Player cnpcplus$getPlayer();
}
