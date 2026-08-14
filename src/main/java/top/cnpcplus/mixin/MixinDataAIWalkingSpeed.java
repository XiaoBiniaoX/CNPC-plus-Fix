package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.WalkingSpeedAccess;

@Mixin(value = DataAI.class, remap = false)
public class MixinDataAIWalkingSpeed implements WalkingSpeedAccess {
    @Shadow(remap = false) public EntityNPCInterface npc;

    @Unique private float cnpcplus$walkingSpeed = 5.0f;

    @Override
    public float cnpcplus$getWalkingSpeed() {
        return this.cnpcplus$walkingSpeed;
    }

    @Override
    public void cnpcplus$setWalkingSpeed(float speed) {
        if (speed < 0.01f || speed > 100.0f) {
            throw new CustomNPCsException("Wrong speed: " + speed);
        }
        this.cnpcplus$walkingSpeed = speed;
        float base = speed / 20.0f;
        this.npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(base);
        this.npc.getAttribute(ForgeMod.SWIM_SPEED.get()).setBaseValue(base * 32.0f);
        this.npc.getAttribute(Attributes.FLYING_SPEED).setBaseValue(base * 2.0f);
    }

    @Inject(method = "setWalkingSpeed", at = @At("RETURN"))
    private void cnpcplus$syncIntegerSpeed(int speed, CallbackInfo ci) {
        this.cnpcplus$walkingSpeed = speed;
    }

    @Inject(method = "readToNBT", at = @At("RETURN"))
    private void cnpcplus$readFloatSpeed(CompoundTag tag, CallbackInfo ci) {
        float speed = tag.contains("CNPCPlusMoveSpeed") ? tag.getFloat("CNPCPlusMoveSpeed") : tag.getInt("MoveSpeed");
        if (speed >= 0.01f) cnpcplus$setWalkingSpeed(speed);
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveFloatSpeed(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        tag.putInt("MoveSpeed", Math.round(this.cnpcplus$walkingSpeed));
        tag.putFloat("CNPCPlusMoveSpeed", this.cnpcplus$walkingSpeed);
    }
}
