package bin.cnpcplus.mixin.animation;

import bin.cnpcplus.animation.SwingDriver;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.util.EnumHand;
import noppes.npcs.client.EntityUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * After EntityUtil.Copy(npc, modelProxy) the model proxy misses attack state:
 * - swingingHand is never copied, so ModelBiped.getMainHand swings the wrong arm
 * - AbstractSkeleton.isSwingingArms is never set, so ModelSkeleton never plays
 *   its melee swing
 * - EntityIronGolem.attackTimer stays 0, so ModelIronGolem never plays the punch
 *
 * The outer NPC's swingProgress is never driven by vanilla (CNPC re-triggers
 * the swing while attacking, so updateArmSwingProgress() always yields 0 and
 * isSwingInProgress stays sticky true). Fix: MixinNetHandlerSwing observes real
 * swingArm() calls and starts a 12-tick synthetic rise/fall swing in SwingDriver
 * (written to the outer NPC, which feeds every biped model via
 * RenderLivingBase.doRender -> mainModel.swingProgress, and to the proxy).
 * When no swing is running the progress is forced to 0, so no stale raised-arm
 * pose can survive.
 */
@Mixin(value = EntityUtil.class, remap = false)
public class MixinEntityUtilSwing {

    @Unique
    private static Field cnpcplus$golemAttackTimer;

    @Inject(method = "Copy", at = @At("RETURN"), remap = false)
    private static void cnpcplus$syncSwingState(EntityLivingBase copied, EntityLivingBase entity, CallbackInfo ci) {
        try {
            entity.swingingHand = copied.swingingHand == null ? EnumHand.MAIN_HAND : copied.swingingHand;

            int tick = SwingDriver.drive(copied, entity);

            if (entity instanceof AbstractSkeleton) {
                ((AbstractSkeleton) entity).setSwingingArms(SwingDriver.isSwinging(copied));
            }
            if (entity instanceof EntityIronGolem) {
                cnpcplus$setGolemAttackTimer(entity, tick);
            }
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static void cnpcplus$setGolemAttackTimer(EntityLivingBase entity, int tick) {
        try {
            if (cnpcplus$golemAttackTimer == null) {
                try {
                    cnpcplus$golemAttackTimer = EntityIronGolem.class.getDeclaredField("field_70855_f");
                } catch (NoSuchFieldException e) {
                    cnpcplus$golemAttackTimer = EntityIronGolem.class.getDeclaredField("attackTimer");
                }
                cnpcplus$golemAttackTimer.setAccessible(true);
            }
            int timer = tick < 0 ? 0 : (int) Math.round(10.0F * (1.0F - tick / 12.0F));
            cnpcplus$golemAttackTimer.setInt(entity, timer);
        } catch (Throwable ignored) {
        }
    }
}
