package bin.cnpcplus.mixin.linked;

import bin.cnpcplus.accessor.LinkedScriptSyncAccess;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LinkedNpcController.class, remap = false)
public class MixinLinkedNpcControllerScriptSync {

    @Inject(method = "readNpcData", at = @At("RETURN"))
    private void cnpcplus$addScriptData(EntityNPCInterface npc, CallbackInfoReturnable<CompoundTag> cir) {
        if (npc == null || npc.linkedData == null) return;
        if (!((LinkedScriptSyncAccess)(Object)npc.linkedData).cnpcplus$isScriptSync()) return;
        npc.script.save(cir.getReturnValue());
    }

    @Inject(method = "loadNpcData", at = @At("TAIL"))
    private void cnpcplus$loadScriptData(EntityNPCInterface npc, CallbackInfo ci) {
        if (npc.linkedData == null) return;
        if (!((LinkedScriptSyncAccess)(Object)npc.linkedData).cnpcplus$isScriptSync()) return;
        CompoundTag compound = npc.linkedData.data;
        if (compound.contains("Scripts") || compound.contains("ScriptEnabled")) {
            npc.script.load(compound);
            npc.script.lastInited = -1L;
            npc.updateAI = true;
        }
    }
}
