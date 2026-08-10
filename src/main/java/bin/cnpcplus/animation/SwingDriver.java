package bin.cnpcplus.animation;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Synthetic 12-tick rise/fall swing driver for the outer NPC. Plain class (not
 * a mixin): mixins can only contain private methods, so shared state and logic
 * live here and both animation mixins call into it.
 */
public class SwingDriver {

    private static final Map<EntityLivingBase, Integer> SWING_TICK = new IdentityHashMap<EntityLivingBase, Integer>();

    public static void startSwing(EntityLivingBase npc) {
        SWING_TICK.put(npc, Integer.valueOf(0));
    }

    /** Advance and apply the curve; returns -1 when idle, else 0..12. */
    public static int drive(EntityLivingBase copied, EntityLivingBase entity) {
        Integer it = SWING_TICK.get(copied);
        int t;
        if (it == null) {
            t = -1;
        } else {
            t = it.intValue() + 1;
            if (t > 12) {
                SWING_TICK.remove(copied);
                t = -1;
            } else {
                SWING_TICK.put(copied, Integer.valueOf(t));
            }
        }
        float p = t < 0 ? 0.0F
                : t <= 6 ? MathHelper.sin(t / 6.0F * (float) Math.PI * 0.5F)
                : MathHelper.sin((12 - t) / 6.0F * (float) Math.PI * 0.5F);
        entity.swingProgress = p;
        entity.prevSwingProgress = p;
        copied.swingProgress = p;
        copied.prevSwingProgress = p;
        return t;
    }

    public static boolean isSwinging(EntityLivingBase copied) {
        return SWING_TICK.containsKey(copied);
    }
}
