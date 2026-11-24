package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IDataMeleeFloatAccess;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityAIAttackTarget.class, remap = false)
public abstract class MixinMeleeAttackInterval {
    @Unique private double cnpcplus$remainder;
    @Unique private float cnpcplus$previousDelay;

    @Redirect(method = "func_75246_d", at = @At(value = "INVOKE", target = "Lnoppes/npcs/entity/data/DataMelee;getDelay()I"), require = 1)
    private int cnpcplus$fractionalInterval(DataMelee melee) {
        float delay = Math.max(1, ((IDataMeleeFloatAccess)melee).cnpcplus$getDelayFloat());
        if (delay != cnpcplus$previousDelay) { cnpcplus$remainder = 0; cnpcplus$previousDelay = delay; }
        double interval = delay + cnpcplus$remainder;
        int ticks = (int)Math.min(Integer.MAX_VALUE, Math.floor(interval));
        cnpcplus$remainder = interval - ticks;
        return ticks;
    }
}
