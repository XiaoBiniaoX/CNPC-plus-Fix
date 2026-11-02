package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.data.NpcHitboxStore;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class MixinLivingEntityHitboxPose {
    @Inject(method = "getLocalBoundsForPose", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$rectangularPose(Pose pose, CallbackInfoReturnable<AABB> cir) {
        if ((Object) this instanceof EntityNPCInterface npc) cir.setReturnValue(NpcHitboxStore.box(npc, cir.getReturnValue()));
    }
}
