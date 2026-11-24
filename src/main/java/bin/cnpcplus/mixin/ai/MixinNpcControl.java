package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.ai.NpcControlAccess;
import bin.cnpcplus.ai.PursuitLimit;
import bin.cnpcplus.ai.ReturnHomeState;
import bin.cnpcplus.wand.WandFreezeState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcControl extends EntityCreature implements NpcControlAccess {
    @Shadow @Final private static DataParameter<Boolean> Walking;
    @Unique private long cnpcplus$freezeEpoch = -1, cnpcplus$disengageUntil;
    @Unique private boolean cnpcplus$forcedReturning;
    protected MixinNpcControl(World world) { super(world); }
    public boolean cnpcplus$isFrozen() {
        return !world.isRemote && cnpcplus$freezeEpoch >= 0 && getServer() != null
                && cnpcplus$freezeEpoch == WandFreezeState.get(getServer()).epoch();
    }
    public void cnpcplus$setFrozen(boolean frozen) {
        cnpcplus$freezeEpoch = frozen ? WandFreezeState.get(getServer()).epoch() : -1;
        if (frozen) {
            getNavigator().clearPath(); motionX = motionY = motionZ = 0;
            setAIMoveSpeed(0); setMoveForward(0); setMoveStrafing(0); setJumping(false);
            dataManager.set(Walking, false);
        }
    }
    public boolean cnpcplus$isDisengaging() {
        return ((PursuitLimit)((EntityNPCInterface)(Object)this).advanced).cnpcplus$isPursuitLimited()
                && world.getTotalWorldTime() < cnpcplus$disengageUntil;
    }
    public boolean cnpcplus$isForcedReturning() { return cnpcplus$forcedReturning; }
    @Inject(method = "func_70071_h_", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$controlTick(CallbackInfo ci) {
        if (world.isRemote) return;
        if (cnpcplus$isFrozen()) { ci.cancel(); return; }
        if (CustomNpcs.FreezeNPCs) return;
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        if (!((PursuitLimit)npc.advanced).cnpcplus$isPursuitLimited()) {
            cnpcplus$forcedReturning = false; cnpcplus$disengageUntil = 0; return;
        }
        EntityLivingBase target = getAttackTarget();
        if (target != null && PursuitLimit.outside(npc, target)) {
            cnpcplus$disengageUntil = world.getTotalWorldTime() + 60;
            cnpcplus$forcedReturning = true;
            npc.combatHandler.reset(); npc.setAttackTarget(null);
            // targetLost scripts cannot veto a configured pursuit boundary.
            super.setAttackTarget(null); getNavigator().clearPath();
        } else if (cnpcplus$forcedReturning && (target != null || ReturnHomeState.arrived(npc, PursuitLimit.home(npc)))) {
            cnpcplus$forcedReturning = false;
        }
    }
    @Inject(method = {"func_70624_b", "onAttack"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void cnpcplus$limitTargets(EntityLivingBase target, CallbackInfo ci) {
        if (target != null && (cnpcplus$isFrozen() || cnpcplus$isDisengaging()
                || PursuitLimit.outside((EntityNPCInterface)(Object)this, target))) ci.cancel();
    }
    @Inject(method = "func_70097_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$freezeDamage(DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (cnpcplus$isFrozen()) cir.setReturnValue(false);
    }
    @Inject(method = "func_70014_b", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveFrozen(NBTTagCompound tag, CallbackInfo ci) {
        if (cnpcplus$isFrozen()) { tag.setBoolean("CNPCPlusFrozen", true); tag.setLong("CNPCPlusFreezeEpoch", cnpcplus$freezeEpoch); }
    }
    @Inject(method = "func_70037_a", at = @At("RETURN"), require = 1)
    private void cnpcplus$loadFrozen(NBTTagCompound tag, CallbackInfo ci) {
        cnpcplus$freezeEpoch = tag.getBoolean("CNPCPlusFrozen") ? tag.getLong("CNPCPlusFreezeEpoch") : -1;
        if (cnpcplus$isFrozen()) cnpcplus$setFrozen(true);
    }
}
