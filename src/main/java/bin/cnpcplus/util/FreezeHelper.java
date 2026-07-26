package bin.cnpcplus.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.mixin.WalkAnimationStateMixin;

public final class FreezeHelper {
    private FreezeHelper() {}

    private static final ThreadLocal<Entity> RENDERING_CNPC_MODEL = new ThreadLocal<>();

    public static void markRenderingModelEntity(Entity modelEntity) {
        RENDERING_CNPC_MODEL.set(modelEntity);
    }

    public static void clearRenderingModelEntity() {
        RENDERING_CNPC_MODEL.remove();
    }

    public static boolean isRenderingCNPCModelEntity(Entity entity) {
        return RENDERING_CNPC_MODEL.get() == entity;
    }

    public static void freezeAnimation(LivingEntity entity) {
        entity.walkDist = 0.0F;
        entity.walkDistO = 0.0F;
        entity.zza = 0.0F;
        entity.xxa = 0.0F;
        entity.walkAnimation.setSpeed(0.0F);
        ((WalkAnimationStateMixin)(Object)entity.walkAnimation).setSpeedOld(0.0F);
        ((WalkAnimationStateMixin)(Object)entity.walkAnimation).setPosition(0.0F);
    }

    public static void freezePosition(LivingEntity entity) {
        entity.xOld = entity.getX();
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ();
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
    }

    public static void freezeRotation(LivingEntity entity) {
        entity.yBodyRotO = entity.yBodyRot;
        entity.yHeadRotO = entity.yHeadRot;
        entity.yRotO = entity.getYRot();
        entity.xRotO = 0.0F;
        entity.setXRot(0.0F);
    }
}