package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.ai.CombatReturnState;
import bin.cnpcplus.ai.ReturnHomeState;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.math.Vec3d;
import noppes.npcs.ai.EntityAIReturn;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityAIReturn.class, remap = false)
public abstract class MixinEntityAIReturnSmoothArrival {
    @Shadow @Final private EntityNPCInterface npc;
    @Shadow private double endPosX;
    @Shadow private double endPosY;
    @Shadow private double endPosZ;
    @Shadow private int stuckTicks;
    @Shadow private int stuckCount;
    @Shadow private int totalTicks;
    @Shadow private boolean wasAttacked;
    @Shadow private double[] preAttackPos;
    @Unique private boolean cnpcplus$retryNearHome;

    @Inject(method = "func_75250_a", at = @At("HEAD"), require = 1)
    private void cnpcplus$useCombatStartPosition(CallbackInfoReturnable<Boolean> cir) {
        double[] home;
        if (((bin.cnpcplus.ai.NpcControlAccess)npc).cnpcplus$isForcedReturning()) {
            Vec3d origin = bin.cnpcplus.ai.PursuitLimit.home(npc);
            home = new double[]{origin.x, origin.y, origin.z};
        } else home = CombatReturnState.destination(npc);
        if (home != null) {
            wasAttacked = true;
            preAttackPos = home;
        }
    }

    @Inject(method = "navigate", at = @At("RETURN"), require = 1)
    private void cnpcplus$trackDestination(boolean towards, CallbackInfo ci) {
        if (CnpcPlusConfig.isReturnHomeSmoothArrival()) {
            ReturnHomeState.start(npc, new Vec3d(endPosX, endPosY, endPosZ));
            npc.setCurrentAnimation(0);
        }
    }

    @Inject(method = "func_75253_b", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$continueToActualDestination(CallbackInfoReturnable<Boolean> cir) {
        Vec3d target = ReturnHomeState.target(npc);
        if (target == null) return;
        cir.setReturnValue(!npc.isFollower() && !npc.isKilled() && !npc.isAttacking()
                && !npc.isInteracting() && !npc.isRiding() && !npc.isBeingRidden()
                && npc.ais.shouldReturnHome() && !ReturnHomeState.arrived(npc, target)
                && totalTicks <= CnpcPlusConfig.getReturnHomeTimeoutTicks());
    }

    @Redirect(method = "func_75246_d", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/pathfinding/PathNavigate;func_75500_f()Z"), require = 1)
    private boolean cnpcplus$letControllerFinish(PathNavigate navigation) {
        return navigation.noPath() && ReturnHomeState.finalApproach(npc) == null;
    }

    @Redirect(method = "func_75246_d", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;func_70107_b(DDD)V"), require = 2)
    private void cnpcplus$deferEarlyTeleport(EntityNPCInterface npc, double x, double y, double z) {
        double tolerance = CnpcPlusConfig.getReturnHomeArrivalTolerance();
        double dx = x - npc.posX, dz = z - npc.posZ;
        cnpcplus$retryNearHome = ReturnHomeState.target(npc) != null
                && totalTicks <= CnpcPlusConfig.getReturnHomeTimeoutTicks()
                && dx * dx + dz * dz <= tolerance * tolerance;
        if (!cnpcplus$retryNearHome) npc.setPosition(x, y, z);
    }

    @Redirect(method = "func_75246_d", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/pathfinding/PathNavigate;func_75499_g()V"), require = 2)
    private void cnpcplus$retryNearHome(PathNavigate navigation) {
        navigation.clearPath();
        if (cnpcplus$retryNearHome) {
            cnpcplus$retryNearHome = false;
            stuckTicks = stuckCount = 0;
            navigation.tryMoveToXYZ(endPosX, endPosY, endPosZ, 1.0D);
        }
    }

    @ModifyConstant(method = {"func_75246_d", "func_75253_b"}, constant = @Constant(intValue = 600), require = 2)
    private int cnpcplus$configurableTimeout(int original) { return CnpcPlusConfig.getReturnHomeTimeoutTicks(); }

    @Inject(method = "func_75251_c", at = @At("RETURN"), require = 1)
    private void cnpcplus$releaseControl(CallbackInfo ci) {
        Vec3d target = ReturnHomeState.end(npc);
        if (target == null || !ReturnHomeState.arrived(npc, target) || npc.isAttacking()
                || npc.isInteracting() || npc.isKilled()) return;
        npc.motionX = npc.motionZ = 0;
        npc.setAIMoveSpeed(0);
        npc.setMoveForward(0);
        npc.setMoveStrafing(0);
        npc.getMoveHelper().setMoveTo(npc.posX, npc.posY, npc.posZ, 0);
        if (npc.ais.getStandingType() == 1) {
            npc.rotationYaw = npc.renderYawOffset = npc.rotationYawHead = npc.ais.orientation;
        }
        npc.setCurrentAnimation(npc.ais.getAnimation());
        npc.updateHitbox();
    }
}
