package bin.cnpcplus.mixin.lifecycle;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve corpse age on tracking and apply received poses even while NPC AI is frozen. */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceReviveHitbox {
    @Shadow protected static DataParameter<Integer> Animation;
    @Shadow private boolean wasKilled;
    @Unique private int cnpcplus$lastAnimation = -1;

    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/NBTTagCompound;", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeCorpseAge(CallbackInfoReturnable<NBTTagCompound> cir) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        cir.getReturnValue().setInteger("CNPCPlusDeathTime", npc.deathTime);
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("TAIL"), require = 1)
    private void cnpcplus$readCorpseAge(NBTTagCompound compound, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!npc.world.isRemote || !compound.hasKey("CNPCPlusDeathTime", 99)) return;
        // Forge applies metadata before additional spawn data. Mark this state as
        // observed so the first native onUpdate does not erase the received age.
        this.wasKilled = npc.isKilled();
        npc.deathTime = this.wasKilled ? Math.max(0, compound.getInteger("CNPCPlusDeathTime")) : 0;
        npc.currentAnimation = npc.getDataManager().get(Animation);
        this.cnpcplus$lastAnimation = npc.currentAnimation;
        npc.updateHitbox();
    }

    @Inject(method = "func_70071_h_", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$syncHitboxWithAnimation(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.world == null || !npc.world.isRemote) return;

        int animation = npc.getDataManager().get(Animation);
        boolean refresh = this.cnpcplus$lastAnimation != animation;
        if (npc.currentAnimation != animation) {
            npc.currentAnimation = animation;
            npc.animationStart = npc.ticksExisted;
            refresh = true;
        }
        // Animation 14 deliberately uses deathTime=19 for its visual pose.
        if (!npc.isKilled() && npc.getHealth() > 0 && animation != 14 && npc.deathTime > 0) {
            npc.deathTime = 0;
            npc.hasDied = false;
            refresh = true;
        }
        this.cnpcplus$lastAnimation = animation;
        if (refresh) npc.updateHitbox();
    }
}
