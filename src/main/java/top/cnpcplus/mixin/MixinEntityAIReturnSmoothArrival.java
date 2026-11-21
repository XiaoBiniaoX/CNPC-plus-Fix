package top.cnpcplus.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
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
import top.cnpcplus.ai.ReturnHomeState;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

@Mixin(value = EntityAIReturn.class, remap = false)
public abstract class MixinEntityAIReturnSmoothArrival {
    @Shadow @Final private EntityNPCInterface npc;
    @Shadow private double endPosX;
    @Shadow private double endPosY;
    @Shadow private double endPosZ;
    @Shadow private int totalTicks;
    @Shadow private int stuckTicks;
    @Shadow private int stuckCount;
    @Unique private boolean cnpcplus$retryNearHome;

    @Inject(method = "navigate", at = @At("RETURN"), require = 1)
    private void cnpcplus$trackReturnDestination(boolean towards, CallbackInfo ci) {
        if (ServerConfigAccess.bool(CnpcPlusServerConfig.ReturnHomeSmoothArrival, true)) {
            ReturnHomeState.start(npc, new Vec3(endPosX, endPosY, endPosZ));
        }
    }

    @Inject(method = "m_8045_", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$continueToActualDestination(CallbackInfoReturnable<Boolean> cir) {
        Vec3 target = ReturnHomeState.target(npc);
        if (target == null) return;
        // After combat CNPC can choose a pre-attack position instead of home.
        cir.setReturnValue(!npc.isFollower() && !npc.isKilled() && !npc.isAttacking()
                && !npc.isInteracting() && !npc.isPassenger() && npc.ais.shouldReturnHome()
                && !ReturnHomeState.arrived(npc, target)
                && totalTicks <= cnpcplus$configurableTimeout(600));
    }

    @Redirect(method = "m_8037_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_26571_()Z"), require = 1)
    private boolean cnpcplus$letFinalControllerFinish(PathNavigation navigation) {
        // Keep the original tick (including the timeout counter) running.
        return navigation.isDone() && ReturnHomeState.finalApproach(npc) == null;
    }

    @Redirect(method = "m_8037_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;m_6034_(DDD)V"), require = 2)
    private void cnpcplus$deferEarlyTeleportNearHome(EntityNPCInterface npc, double x, double y, double z) {
        double tolerance = ServerConfigAccess.decimal(CnpcPlusServerConfig.ReturnHomeArrivalTolerance, 3.0);
        double dx = x - npc.getX(), dz = z - npc.getZ();
        cnpcplus$retryNearHome = ReturnHomeState.target(npc) != null
                && totalTicks <= cnpcplus$configurableTimeout(600) && dx * dx + dz * dz <= tolerance * tolerance;
        if (!cnpcplus$retryNearHome) npc.setPos(x, y, z);
    }

    @Redirect(method = "m_8037_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_26573_()V"), require = 2)
    private void cnpcplus$retryNearHome(PathNavigation navigation) {
        navigation.stop();
        if (cnpcplus$retryNearHome) {
            cnpcplus$retryNearHome = false;
            stuckTicks = stuckCount = 0;
            navigation.moveTo(endPosX, endPosY, endPosZ, 1.0);
        }
    }

    @ModifyConstant(method = {"m_8037_", "m_8045_"}, constant = @Constant(intValue = 600), require = 2)
    private int cnpcplus$configurableTimeout(int original) {
        return ServerConfigAccess.integer(CnpcPlusServerConfig.ReturnHomeTimeoutSeconds, 30) * 20;
    }

    @Inject(method = "m_8041_", at = @At("RETURN"), require = 1)
    private void cnpcplus$releaseReturnControl(CallbackInfo ci) {
        Vec3 target = ReturnHomeState.end(npc);
        if (target == null || !ReturnHomeState.arrived(npc, target) || npc.isAttacking()
                || npc.isInteracting() || npc.isKilled()) return;
        if (npc.ais.getStandingType() == 1) {
            float yaw = npc.ais.orientation;
            npc.setYRot(yaw);
            npc.setYBodyRot(yaw);
            npc.setYHeadRot(yaw);
        }
        npc.setCurrentAnimation(npc.ais.getAnimation());
        npc.refreshDimensions();
        npc.updateAI = true;
    }
}
