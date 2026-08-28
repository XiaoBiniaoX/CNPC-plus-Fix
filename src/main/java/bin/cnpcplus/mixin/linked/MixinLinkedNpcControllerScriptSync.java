package bin.cnpcplus.mixin.linked;

import bin.cnpcplus.linked.LinkedScriptSync;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.controllers.LinkedNpcController.LinkedData;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LinkedNpcController.class, remap = false)
public abstract class MixinLinkedNpcControllerScriptSync {

    @Inject(method = "loadNpcData", at = @At("TAIL"))
    private void cnpcplus$onLoadNpcData(EntityNPCInterface npc, CallbackInfo ci) {
        if (npc.linkedName == null || npc.linkedName.isEmpty()) {
            return;
        }
        LinkedData data = LinkedNpcController.Instance.getData(npc.linkedName);
        if (data == null) {
            return;
        }
        LinkedScriptSync.load(npc, data);
    }

    @Inject(method = "readNpcData", at = @At("RETURN"))
    private void cnpcplus$includeScriptData(EntityNPCInterface npc, CallbackInfoReturnable<NBTTagCompound> cir) {
        LinkedScriptSync.include(npc, cir.getReturnValue());
    }
}
