package bin.cnpcplus.mixin.knockback;

import bin.cnpcplus.common.KnockbackResistanceUtil;
import net.minecraft.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceKnockback {
    @Inject(method = "func_70653_a(Lnet/minecraft/entity/Entity;FDD)V", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$immuneKnockback(Entity attacker, float strength, double x, double z, CallbackInfo ci) {
        // Native CNPC multiplies strength by 2-resistance before invoking its parent.
        if (((EntityNPCInterface)(Object)this).stats.resistances.knockback >= 2) ci.cancel();
    }
    @Redirect(method = "func_70652_k(Lnet/minecraft/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;func_70024_g(DDD)V"), remap = false, require = 1)
    private void cnpcplus$scaleMeleeKnockback(Entity target, double x, double y, double z) {
        // addVelocity is the MCP name; reobf maps it back to func_70024_g.
        target.addVelocity(KnockbackResistanceUtil.scale(target, x), KnockbackResistanceUtil.scale(target, y),
                KnockbackResistanceUtil.scale(target, z));
    }
}
