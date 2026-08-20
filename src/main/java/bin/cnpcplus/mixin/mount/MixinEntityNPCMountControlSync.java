package bin.cnpcplus.mixin.mount;

import bin.cnpcplus.common.IMountControlData;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carries MountControl over to the client.
 *
 * writeSpawnData hand-picks the fields the client needs (Speed, MovingState,
 * Orientation, ...) and never calls ais.writeToNBT, so a flag added to DataAI is
 * server-only. Logs showed exactly that: the same npc read mountControl=true on
 * the server and false on the client, so the client key sender skipped every
 * tick and no input packet was ever sent. It appeared to work only right after
 * the AI screen had been open, because MainmenuAIGet is the one path that does
 * ship the whole DataAI compound.
 *
 * writeSpawnData()/readSpawnData(NBTTagCompound) are the pair used both by the
 * initial spawn packet and by updateClient(), so one injection covers both.
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCMountControlSync {
    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/NBTTagCompound;",
            at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$writeMountControl(CallbackInfoReturnable<NBTTagCompound> cir) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        NBTTagCompound compound = cir.getReturnValue();
        if (compound == null || npc.ais == null) {
            return;
        }
        compound.setBoolean("MountControl",
                ((IMountControlData) (Object) npc.ais).cnpcplus$getMountControl());
    }

    /**
     * Only assigns when the key is present. writeSpawnData has an early return for
     * non-EntityCustomNpc (offset before ModelData), and any other caller building
     * a partial compound would otherwise silently clear the flag back to false.
     */
    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/NBTTagCompound;)V",
            at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$readMountControl(NBTTagCompound compound, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (compound == null || npc.ais == null || !compound.hasKey("MountControl")) {
            return;
        }
        ((IMountControlData) (Object) npc.ais)
                .cnpcplus$setMountControl(compound.getBoolean("MountControl"));
    }
}
