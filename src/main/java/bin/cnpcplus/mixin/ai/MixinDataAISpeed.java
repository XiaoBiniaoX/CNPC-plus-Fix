package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.ai.AiSpeedAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataAI.class, remap = false)
public class MixinDataAISpeed implements AiSpeedAccess {
    @Shadow
    private EntityNPCInterface npc;

    @Shadow
    private int moveSpeed;

    @Unique
    private float cnpcplus$moveSpeed = 5.0F;

    @Inject(method = "setWalkingSpeed", at = @At("HEAD"))
    private void cnpcplus$syncIntegerSpeed(int speed, CallbackInfo ci) {
        this.cnpcplus$moveSpeed = Math.max(0.01F, speed);
    }

    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpcplus$readFloatSpeed(CompoundTag tag, CallbackInfo ci) {
        float speed = tag.contains("CNPCPlusMoveSpeed")
                ? tag.getFloat("CNPCPlusMoveSpeed")
                : tag.getInt("MoveSpeed");
        if (speed >= 0.01F) this.cnpcplus$setWalkingSpeed(speed);
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveFloatSpeed(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag result = cir.getReturnValue();
        result.putInt("MoveSpeed", Math.round(this.cnpcplus$moveSpeed));
        result.putFloat("CNPCPlusMoveSpeed", this.cnpcplus$moveSpeed);
    }

    @Override
    public float cnpcplus$getWalkingSpeed() {
        return this.cnpcplus$moveSpeed;
    }

    @Override
    public void cnpcplus$setWalkingSpeed(float speed) {
        if (!Float.isFinite(speed)) speed = 5.0F;
        this.cnpcplus$moveSpeed = Math.max(0.01F, Math.min(100.0F, speed));
        this.moveSpeed = Math.round(this.cnpcplus$moveSpeed);
        float actual = this.cnpcplus$moveSpeed / 20.0F;
        this.npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(actual);
        this.npc.getAttribute(Attributes.FLYING_SPEED).setBaseValue(actual * 2.0F);
    }
}
