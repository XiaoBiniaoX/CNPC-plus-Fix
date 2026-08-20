package bin.cnpcplus.mixin.knockback;

import bin.cnpcplus.common.KnockbackResistanceUtil;
import net.minecraft.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceKnockback {
    @Redirect(method = "func_70652_k(Lnet/minecraft/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;func_70024_g(DDD)V"), remap = false)
    private void cnpcplus$scaleMeleeKnockback(Entity target, double x, double y, double z) {
        // addVelocity is the MCP name; reobf maps it back to func_70024_g.
        target.addVelocity(KnockbackResistanceUtil.scale(target, x), y,
                KnockbackResistanceUtil.scale(target, z));
    }
}
