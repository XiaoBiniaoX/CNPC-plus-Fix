package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceKnockback {

    @Shadow(remap = false)
    public noppes.npcs.entity.data.DataStats stats;

    @ModifyArg(method = "m_7327_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(DDD)V", ordinal = 0), index = 1)
    private double modifyKnockbackY(double y) {
        return 0.1 + this.stats.melee.getKnockback() * 0.04;
    }

    @Redirect(method = "m_7327_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/entity/data/DataMelee;getKnockback()I", ordinal = 0, remap = false), remap = false)
    private int redirectKnockbackCheck(DataMelee melee) {
        int kb = melee.getKnockback();
        return kb >= 0 ? kb : -kb;
    }
}
