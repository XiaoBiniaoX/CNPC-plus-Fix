package top.cnpcplus.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ai.EntityAIRangedAttack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityAIRangedAttack.class, remap = false)
public class MixinEntityAIRangedAttackCanUse {

    @Shadow
    private EntityNPCInterface npc;

    @Redirect(method = "m_8036_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/entity/EntityNPCInterface;isInRange(Lnet/minecraft/world/entity/Entity;D)Z", ordinal = 0), remap = false)
    private boolean redirectCanUseRange(EntityNPCInterface npc, net.minecraft.world.entity.Entity target, double range) {
        double maxRange = Math.max(npc.stats.aggroRange, npc.stats.ranged.getRange());
        return npc.isInRange(target, maxRange);
    }
}
