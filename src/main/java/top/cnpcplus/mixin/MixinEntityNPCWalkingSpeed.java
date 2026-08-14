package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.WalkingSpeedAccess;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCWalkingSpeed {
    @Shadow(remap = false) public DataAI ais;

    @Inject(method = "m_6113_", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$getPreciseSpeed(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(((WalkingSpeedAccess) this.ais).cnpcplus$getWalkingSpeed() / 20.0f);
    }

    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void cnpcplus$writePreciseSpeed(CallbackInfoReturnable<CompoundTag> cir) {
        float speed = ((WalkingSpeedAccess) this.ais).cnpcplus$getWalkingSpeed();
        cir.getReturnValue().putInt("Speed", Math.round(speed));
        cir.getReturnValue().putFloat("CNPCPlusSpeed", speed);
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void cnpcplus$readPreciseSpeed(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("CNPCPlusSpeed")) {
            ((WalkingSpeedAccess) this.ais).cnpcplus$setWalkingSpeed(tag.getFloat("CNPCPlusSpeed"));
        }
    }
}
