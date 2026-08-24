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

    /**
     * 用小数速度替代原版的 `getWalkingSpeed() / 20`。
     *
     * <p>cnpcplus$getWalkingSpeed() 在没有小数覆盖值时会回落到原版 int 字段，
     * 所以这里的返回值与原版等价，不会把 NPC 按成固定速度（曾因字段初始值 5.0f 导致罚站）。
     * 仍加一道下限保护：拿到非正值就交回原版逻辑，绝不返回 0。
     */
    @Inject(method = "m_6113_", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$getPreciseSpeed(CallbackInfoReturnable<Float> cir) {
        if (this.ais == null) return;
        float speed = ((WalkingSpeedAccess) this.ais).cnpcplus$getWalkingSpeed();
        if (speed < 0.01f) return;
        cir.setReturnValue(speed / 20.0f);
    }

    /**
     * spawn 数据：只在真有小数覆盖值时才多写键，不覆盖原版已写好的 Speed。
     * 原来无条件 putInt("Speed", round(字段))，字段为初始值时会把正确速度写坏。
     */
    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void cnpcplus$writePreciseSpeed(CallbackInfoReturnable<CompoundTag> cir) {
        if (this.ais == null) return;
        float speed = ((WalkingSpeedAccess) this.ais).cnpcplus$getWalkingSpeed();
        if (speed < 0.01f) return;
        cir.getReturnValue().putInt("Speed", Math.round(speed));
        cir.getReturnValue().putFloat("CNPCPlusSpeed", speed);
    }

    /** 读 spawn 数据：非法值静默忽略，不抛异常（抛异常会中断实体同步）。 */
    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void cnpcplus$readPreciseSpeed(CompoundTag tag, CallbackInfo ci) {
        if (this.ais == null || !tag.contains("CNPCPlusSpeed")) return;
        float speed = tag.getFloat("CNPCPlusSpeed");
        if (speed >= 0.01f && speed <= 100.0f) {
            ((WalkingSpeedAccess) this.ais).cnpcplus$setWalkingSpeed(speed);
        }
    }
}
