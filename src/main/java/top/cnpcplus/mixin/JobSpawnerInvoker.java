package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 JobSpawner 私有方法 spawnEntity(CompoundTag)。 */
@Mixin(value = JobSpawner.class, remap = false)
public interface JobSpawnerInvoker {
    @Invoker("spawnEntity")
    LivingEntity cnpcplus$spawnEntity(CompoundTag compound);
}
