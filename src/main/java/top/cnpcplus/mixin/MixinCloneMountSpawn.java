package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import noppes.npcs.packets.server.SPacketToolMobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.clone.CloneMounts;

@Mixin(value = SPacketToolMobSpawner.class, remap = false)
public abstract class MixinCloneMountSpawn {
    @Inject(method = "spawnClone", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$spawnWholeMount(CompoundTag tag, double x, double y, double z, Level world, CallbackInfoReturnable<Entity> cir) {
        cir.setReturnValue(CloneMounts.spawn(tag, x, y, z, world, null));
    }
}
