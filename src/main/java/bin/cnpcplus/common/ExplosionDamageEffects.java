package bin.cnpcplus.common;

import java.util.function.Consumer;
import net.minecraft.entity.EntityLivingBase;

/** Early vanilla hook must not resolve CNPC classes before late mixins. */
public final class ExplosionDamageEffects {
    public static final ThreadLocal<Consumer<EntityLivingBase>> CURRENT = new ThreadLocal<>();
    private ExplosionDamageEffects() { }
}
