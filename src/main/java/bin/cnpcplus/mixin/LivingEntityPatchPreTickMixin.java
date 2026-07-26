package bin.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch", remap = false)
public class LivingEntityPatchPreTickMixin {

    @Redirect(method = "preTick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;deathTime:I", opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void cnpcplus$preventDeathTimeDecrement(LivingEntity entity, int value) {
        if (entity instanceof EntityNPCInterface) {
            return;
        }
        entity.deathTime = value;
    }
}