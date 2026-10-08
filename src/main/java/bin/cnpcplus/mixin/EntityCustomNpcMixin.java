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
    /** 客户端是否见过这个 NPC 活着。用于区分「正常击杀（要播倒地动画）」与「区块重载一见面就已死（不重播）」。 */
    @Unique private boolean cnpcplus$everAlive;

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
            cnpcplus$everAlive = true;
            cnpcplus$modelDeathPosSaved = false;
            // 复活后必须把死亡期间对模型实体做的强制状态复位，否则模型会持续隐形、
            // 或停在死亡姿势上，表现就是「复活后模型与碰撞箱不一致」。
            Entity modelEntity = self.modelData.getEntity(self);
            if (modelEntity instanceof LivingEntity living) {
                if (living.isInvisible()) living.setInvisible(false);
                if (living.deathTime != 0) living.deathTime = 0;
                if (living.getHealth() <= 0.0F) living.setHealth(living.getMaxHealth());
            }
            if (self.level().isClientSide && self.deathTime != 0) self.deathTime = 0;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cnpcplus$freezeDeadNpcAndModel(CallbackInfo ci) {
        EntityCustomNpc self = (EntityCustomNpc)(Object)this;
        if (!self.isKilled()) return;

        // 只有「客户端一见面就已死」（区块重载/新进视野）才把 deathTime 钉到 20 免得重播动画。
        // 正常在眼前击杀的 NPC 曾经活着（everAlive），必须让 deathTime 从 0 自然涨到 20，
        // 否则原版倒地动画（LivingEntityRenderer 的 Z 轴翻转，约 0.65s 摆平）一帧都不播，
        // 且同帧越过 >20 的隐藏阈值 → 站着→瞬间平躺→消失，非常突兀。
        if (self.level().isClientSide && !cnpcplus$everAlive && self.deathTime < 20) self.deathTime = 20;

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
