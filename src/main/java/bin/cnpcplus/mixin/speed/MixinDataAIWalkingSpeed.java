package bin.cnpcplus.mixin.speed;

import bin.cnpcplus.speed.WalkingSpeedFloatAccess;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataAI.class, remap = false)
public class MixinDataAIWalkingSpeed implements WalkingSpeedFloatAccess {
    @Unique private float cnpcplus$walkingSpeed = 5.0F;
    @Unique private EntityNPCInterface cnpcplus$npc;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void cnpcplus$init(EntityNPCInterface npc, CallbackInfo ci) {
        this.cnpcplus$npc = npc;
    }

    @Inject(method = "setWalkingSpeed", at = @At("RETURN"), remap = false)
    private void cnpcplus$acceptLegacySpeed(int speed, CallbackInfo ci) {
        this.cnpcplus$walkingSpeed = speed;
        cnpcplus$applyAttributes();
    }

    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readFloatSpeed(NBTTagCompound compound, CallbackInfo ci) {
        float speed = compound.hasKey("CNPCPlusMoveSpeed")
                ? compound.getFloat("CNPCPlusMoveSpeed") : compound.getInteger("MoveSpeed");
        if (speed >= 0.01F) cnpcplus$setWalkingSpeed(speed);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeFloatSpeed(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        compound.setInteger("MoveSpeed", Math.round(this.cnpcplus$walkingSpeed));
        compound.setFloat("CNPCPlusMoveSpeed", this.cnpcplus$walkingSpeed);
    }

    @Override
    public float cnpcplus$getWalkingSpeedFloat() {
        return this.cnpcplus$walkingSpeed;
    }

    @Override
    public void cnpcplus$setWalkingSpeed(float speed) {
        if (Float.isNaN(speed) || Float.isInfinite(speed) || speed < 0.01F || speed > 10.0F) {
            throw new CustomNPCsException("Wrong speed: " + speed, new Object[0]);
        }
        this.cnpcplus$walkingSpeed = speed;
        cnpcplus$applyAttributes();
    }

    public float getWalkingSpeedFloat() {
        return cnpcplus$getWalkingSpeedFloat();
    }

    public void setWalkingSpeed(float speed) {
        cnpcplus$setWalkingSpeed(speed);
    }

    @Unique
    private void cnpcplus$applyAttributes() {
        if (this.cnpcplus$npc == null) return;
        float speed = this.cnpcplus$walkingSpeed / 20.0F;
        this.cnpcplus$npc.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(speed);
        this.cnpcplus$npc.getEntityAttribute(SharedMonsterAttributes.FLYING_SPEED).setBaseValue(speed * 2.0F);
    }
}
