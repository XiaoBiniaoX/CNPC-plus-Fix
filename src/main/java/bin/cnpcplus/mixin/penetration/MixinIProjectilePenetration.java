package bin.cnpcplus.mixin.penetration;

import bin.cnpcplus.common.IProjectilePenetration;
import noppes.npcs.api.entity.IProjectile;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = IProjectile.class, remap = false)
public interface MixinIProjectilePenetration extends IProjectilePenetration {
}
