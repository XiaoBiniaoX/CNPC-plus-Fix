package bin.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityCustomNpc.class)
public class EntityCustomNpcMixin {

    @Unique private double cnpcplus$savedX;
    @Unique private double cnpcplus$savedY;
    @Unique private double cnpcplus$savedZ;
    @Unique private float cnpcplus$savedYRot;
    @Unique private float cnpcplus$savedYBodyRot;
    @Unique private float cnpcplus$savedYHeadRot;
    @Unique private double cnpcplus$modelDeathX;
    @Unique private double cnpcplus$modelDeathY;
    @Unique private double cnpcplus$modelDeathZ;
    @Unique private boolean cnpcplus$modelDeathPosSaved;

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/EntityUtil;Copy(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)V"), remap = false)
    private void cnpcplus$redirectEntityCopy(LivingEntity copied, LivingEntity entity) {
        EntityCustomNpc self = (EntityCustomNpc)(Object)this;
        if (!self.isKilled()) {
            noppes.npcs.client.EntityUtil.Copy(copied, entity);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;tick()V"), remap = false)
    private void cnpcplus$skipModelEntityTick(LivingEntity entity) {
        EntityCustomNpc self = (EntityCustomNpc)(Object)this;
        if (!self.isKilled()) {
            entity.tick();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void cnpcplus$savePosition(CallbackInfo ci) {
        EntityCustomNpc self = (EntityCustomNpc)(Object)this;
        cnpcplus$savedX = self.getX();
        cnpcplus$savedY = self.getY();
        cnpcplus$savedZ = self.getZ();
        cnpcplus$savedYRot = self.getYRot();
        cnpcplus$savedYBodyRot = self.yBodyRot;
        cnpcplus$savedYHeadRot = self.yHeadRot;

        if (self.isKilled()) {
            Entity modelEntity = self.modelData.getEntity(self);
            if (modelEntity != null && !cnpcplus$modelDeathPosSaved) {
                cnpcplus$modelDeathX = modelEntity.getX();
                cnpcplus$modelDeathY = modelEntity.getY();
                cnpcplus$modelDeathZ = modelEntity.getZ();
                cnpcplus$modelDeathPosSaved = true;
            }
        } else {
            cnpcplus$modelDeathPosSaved = false;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cnpcplus$freezeDeadNpcAndModel(CallbackInfo ci) {
        EntityCustomNpc self = (EntityCustomNpc)(Object)this;
        if (!self.isKilled()) return;

        // 已死亡 NPC 的客户端模型不应在远距离重新加载后从死亡动画第 0 帧重播。
        if (self.level().isClientSide && self.deathTime < 20) self.deathTime = 20;

        self.setPos(cnpcplus$savedX, cnpcplus$savedY, cnpcplus$savedZ);
        self.setYRot(cnpcplus$savedYRot);
        self.yBodyRot = cnpcplus$savedYBodyRot;
        self.yHeadRot = cnpcplus$savedYHeadRot;
        self.setDeltaMovement(0.0, 0.0, 0.0);

        bin.cnpcplus.util.FreezeHelper.freezeAnimation(self);
        bin.cnpcplus.util.FreezeHelper.freezePosition(self);
        self.attackAnim = 0.0F;
        self.oAttackAnim = 0.0F;
        self.hurtTime = 0;

        Entity modelEntity = self.modelData.getEntity(self);
        if (modelEntity instanceof LivingEntity living) {
            living.setHealth(0.0F);
            living.deathTime = self.deathTime;
            living.setPos(cnpcplus$modelDeathX, cnpcplus$modelDeathY, cnpcplus$modelDeathZ);
            living.setYRot(cnpcplus$savedYRot);
            living.setDeltaMovement(0.0, 0.0, 0.0);
            bin.cnpcplus.util.FreezeHelper.freezePosition(living);
            bin.cnpcplus.util.FreezeHelper.freezeAnimation(living);
            bin.cnpcplus.util.FreezeHelper.freezeRotation(living);
            living.attackAnim = 0.0F;
            living.oAttackAnim = 0.0F;
            living.hurtTime = 0;

            if (self.deathTime > 20 && self.stats.hideKilledBody) {
                living.setInvisible(true);
            }
        }
    }
}
