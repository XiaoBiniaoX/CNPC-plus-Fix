package bin.cnpcplus.mixin;

import bin.cnpcplus.ai.AiSpeedAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(EntityNPCInterface.class)
public class EntityNPCInterfaceMixin {

    private static EntityDataAccessor<Integer> FactionData;

    private static EntityDataAccessor<Integer> getFactionData() {
        if (FactionData == null) {
            try {
                Field field = EntityNPCInterface.class.getDeclaredField("FactionData");
                field.setAccessible(true);
                FactionData = (EntityDataAccessor<Integer>) field.get(null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to access FactionData", e);
            }
        }
        return FactionData;
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$getName(CallbackInfoReturnable<Component> cir) {
        EntityNPCInterface self = (EntityNPCInterface)(Object)this;
        String raw = self.display.getName();
        if (raw.indexOf('&') >= 0) {
            cir.setReturnValue(Component.literal(raw.replace('&', '\u00a7')));
        }
    }

    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeSpawnData(CallbackInfoReturnable<CompoundTag> cir) {
        EntityNPCInterface self = (EntityNPCInterface)(Object)this;
        CompoundTag tag = cir.getReturnValue();
        if (tag == null) {
            tag = new CompoundTag();
            cir.setReturnValue(tag);
        }
        tag.putInt("Faction", self.getFaction().id);
        float speed = ((AiSpeedAccess) self.ais).cnpcplus$getWalkingSpeed();
        tag.putInt("Speed", Math.round(speed));
        tag.putFloat("CNPCPlusSpeed", speed);
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), remap = false)
    private void cnpcplus$readSpawnData(CompoundTag compound, CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface)(Object)this;
        if (compound.contains("Faction")) {
            self.getEntityData().set(getFactionData(), compound.getInt("Faction"));
        }
        if (compound.contains("CNPCPlusSpeed")) {
            ((AiSpeedAccess) self.ais).cnpcplus$setWalkingSpeed(compound.getFloat("CNPCPlusSpeed"));
        }
    }

    @Inject(method = "getSpeed", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$getFloatSpeed(CallbackInfoReturnable<Float> cir) {
        EntityNPCInterface self = (EntityNPCInterface)(Object)this;
        cir.setReturnValue(((AiSpeedAccess) self.ais).cnpcplus$getWalkingSpeed() / 20.0F);
    }
}
