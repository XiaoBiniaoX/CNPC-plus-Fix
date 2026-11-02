package bin.cnpcplus.mixin.clone;

import bin.cnpcplus.clone.CloneMounts;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import noppes.npcs.NoppesUtilServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NoppesUtilServer.class, remap = false)
public abstract class MixinCloneMountSpawn {
    @Inject(method = "spawnClone", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$spawn(NBTTagCompound tag, double x, double y, double z, World world, CallbackInfoReturnable<Entity> cir) {
        cir.setReturnValue(CloneMounts.spawn(tag, x, y, z, world, null));
    }
}
