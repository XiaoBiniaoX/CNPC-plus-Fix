package bin.cnpcplus.mixin.speed;

import bin.cnpcplus.speed.WalkingSpeedFloatAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCWalkingSpeed {
    @Inject(method = "getSpeed", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$getFloatSpeed(CallbackInfoReturnable<Float> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        cir.setReturnValue(((WalkingSpeedFloatAccess) self.ais).cnpcplus$getWalkingSpeedFloat() / 20.0F);
    }

    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/NBTTagCompound;", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeSpawnSpeed(CallbackInfoReturnable<NBTTagCompound> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        cir.getReturnValue().setInteger("Speed", Math.round(
                ((WalkingSpeedFloatAccess) self.ais).cnpcplus$getWalkingSpeedFloat()));
        cir.getReturnValue().setFloat("CNPCPlusSpeed",
                ((WalkingSpeedFloatAccess) self.ais).cnpcplus$getWalkingSpeedFloat());
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"), remap = false)
    private void cnpcplus$readSpawnSpeed(NBTTagCompound compound, CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (compound.hasKey("CNPCPlusSpeed")) {
            ((WalkingSpeedFloatAccess) self.ais).cnpcplus$setWalkingSpeed(compound.getFloat("CNPCPlusSpeed"));
        }
    }
}
