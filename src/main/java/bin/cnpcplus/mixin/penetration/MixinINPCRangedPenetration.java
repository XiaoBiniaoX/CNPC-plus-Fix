package bin.cnpcplus.mixin.penetration;

import bin.cnpcplus.common.IRangedPenetration;
import noppes.npcs.api.entity.data.INPCRanged;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = INPCRanged.class, remap = false)
public interface MixinINPCRangedPenetration extends IRangedPenetration {
}
