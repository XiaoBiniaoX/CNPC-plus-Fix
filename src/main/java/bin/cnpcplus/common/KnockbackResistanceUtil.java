package bin.cnpcplus.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

public final class KnockbackResistanceUtil {
    private KnockbackResistanceUtil() {
    }

    public static double scale(Entity target, double knockback) {
        if (!(target instanceof EntityLivingBase)) {
            return knockback;
        }
        EntityLivingBase living = (EntityLivingBase) target;
        IAttributeInstance attribute = living.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return knockback;
        }
        double resistance = attribute.getAttributeValue();
        if (Double.isNaN(resistance) || Double.isInfinite(resistance)) {
            return knockback;
        }
        resistance = Math.max(0.0D, Math.min(1.0D, resistance));
        return knockback * (1.0D - resistance);
    }
}
