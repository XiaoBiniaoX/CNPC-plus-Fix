package bin.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import noppes.npcs.client.EntityUtil;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.mixin.WalkAnimationStateMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(EntityCustomNpc.class)
public class EntityCustomNpcMixin {

    private float cnpcplus$savedYBodyRot;
    private float cnpcplus$savedYBodyRotO;
    private float cnpcplus$savedYHeadRot;
    private float cnpcplus$savedYHeadRotO;
    private float cnpcplus$savedYRot;
    private float cnpcplus$savedYRotO;
    private float cnpcplus$savedXRot;
    private float cnpcplus$savedXRotO;
    private double cnpcplus$savedX;
    private double cnpcplus$savedY;
    private double cnpcplus$savedZ;
    private int cnpcplus$savedDeathTime;

    private static Field cnpcplus$walkAnimationPositionField;
    private static Field cnpcplus$walkAnimationPositionOldField;

    static {
        try {
            cnpcplus$walkAnimationPositionField = WalkAnimationState.class.getDeclaredField("position");
            cnpcplus$walkAnimationPositionField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                cnpcplus$walkAnimationPositionField = WalkAnimationState.class.getDeclaredField("f_268522_");
                cnpcplus$walkAnimationPositionField.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                cnpcplus$walkAnimationPositionField = null;
            }
        }
        try {
            cnpcplus$walkAnimationPositionOldField = WalkAnimationState.class.getDeclaredField("positionOld");
            cnpcplus$walkAnimationPositionOldField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                cnpcplus$walkAnimationPositionOldField = WalkAnimationState.class.getDeclaredField("f_268632_");
                cnpcplus$walkAnimationPositionOldField.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                cnpcplus$walkAnimationPositionOldField = null;
            }
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tick()V", remap = false), remap = false)
    private void cnpcplus$redirectModelEntityTick(LivingEntity entity) {
        EntityCustomNpc self = (EntityCustomNpc) (Object) this;
        if (!self.isKilled()) {
            entity.tick();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/EntityUtil;Copy(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)V"), remap = false)
    private void cnpcplus$redirectEntityCopy(LivingEntity copied, LivingEntity entity) {
        EntityCustomNpc self = (EntityCustomNpc) (Object) this;
        if (!self.isKilled()) {
            EntityUtil.Copy(copied, entity);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void cnpcplus$saveRotation(CallbackInfo ci) {
        EntityCustomNpc self = (EntityCustomNpc) (Object) this;
        if (self.isClientSide() && self.isKilled()) {
            self.setDeltaMovement(0.0, 0.0, 0.0);
            this.cnpcplus$savedYBodyRot = self.yBodyRot;
            this.cnpcplus$savedYBodyRotO = self.yBodyRotO;
            this.cnpcplus$savedYHeadRot = self.yHeadRot;
            this.cnpcplus$savedYHeadRotO = self.yHeadRotO;
            this.cnpcplus$savedYRot = self.getYRot();
            this.cnpcplus$savedYRotO = self.yRotO;
            this.cnpcplus$savedXRot = self.getXRot();
            this.cnpcplus$savedXRotO = self.xRotO;
            this.cnpcplus$savedX = self.getX();
            this.cnpcplus$savedY = self.getY();
            this.cnpcplus$savedZ = self.getZ();
            this.cnpcplus$savedDeathTime = self.deathTime;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cnpcplus$freezeDeadNpcAndModel(CallbackInfo ci) {
        EntityCustomNpc self = (EntityCustomNpc) (Object) this;
        if (self.isClientSide() && self.isKilled()) {
            self.setPos(this.cnpcplus$savedX, this.cnpcplus$savedY, this.cnpcplus$savedZ);

            cnpcplus$freezeEntity(self, this.cnpcplus$savedYBodyRot, this.cnpcplus$savedYBodyRotO,
                this.cnpcplus$savedYHeadRot, this.cnpcplus$savedYHeadRotO,
                this.cnpcplus$savedYRot, this.cnpcplus$savedYRotO);

            // CNPC sets deathTime to 19 in tick(), override with natural 0→20 progression
            // tickDeath() already incremented it by 1 from saved value
            int naturalDeathTime = this.cnpcplus$savedDeathTime + 1;
            if (self.stats.hideKilledBody) {
                self.deathTime = naturalDeathTime > 20 ? 21 : naturalDeathTime;
            } else {
                self.deathTime = Math.min(naturalDeathTime, 20);
            }
            self.hurtTime = 0;

            LivingEntity entity = self.modelData.getEntity(self);
            if (entity != null) {
                entity.setPos(self.getX(), self.getY(), self.getZ());
                entity.setDeltaMovement(0.0, 0.0, 0.0);
                cnpcplus$freezeAnimation(entity);
                cnpcplus$freezePosition(entity);
                entity.yBodyRotO = entity.yBodyRot;
                entity.yHeadRotO = entity.yHeadRot;
                entity.yRotO = entity.getYRot();
                entity.xRotO = 0.0F;
                entity.setXRot(0.0F);
                entity.attackAnim = 0.0F;
                entity.oAttackAnim = 0.0F;
                entity.swingTime = 0;
                entity.deathTime = Math.min(self.deathTime, 20);
                entity.hurtTime = 0;
            }
        }
    }

    private static void cnpcplus$freezeEntity(LivingEntity entity, float yBodyRot, float yBodyRotO,
                                               float yHeadRot, float yHeadRotO,
                                               float yRot, float yRotO) {
        entity.yBodyRot = yBodyRot;
        entity.yBodyRotO = yBodyRotO;
        entity.yHeadRot = yHeadRot;
        entity.yHeadRotO = yHeadRotO;
        entity.setYRot(yRot);
        entity.yRotO = yRotO;
        entity.setXRot(0.0F);
        entity.xRotO = 0.0F;
        entity.setDeltaMovement(0.0, 0.0, 0.0);
        cnpcplus$freezeAnimation(entity);
        cnpcplus$freezePosition(entity);
    }

    private static void cnpcplus$freezeAnimation(LivingEntity entity) {
        entity.walkDist = 0.0F;
        entity.walkDistO = 0.0F;
        entity.zza = 0.0F;
        entity.xxa = 0.0F;
        entity.walkAnimation.setSpeed(0.0F);
        ((WalkAnimationStateMixin)(Object)entity.walkAnimation).setSpeedOld(0.0F);
        ((WalkAnimationStateMixin)(Object)entity.walkAnimation).setPosition(0.0F);
        try {
            if (cnpcplus$walkAnimationPositionOldField != null) {
                cnpcplus$walkAnimationPositionOldField.setFloat(entity.walkAnimation, 0.0F);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static void cnpcplus$freezePosition(LivingEntity entity) {
        entity.xOld = entity.getX();
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ();
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
    }
}