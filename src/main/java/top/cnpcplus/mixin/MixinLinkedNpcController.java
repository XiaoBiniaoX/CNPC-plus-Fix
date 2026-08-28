package top.cnpcplus.mixin;

import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = LinkedNpcController.class, remap = false)
public class MixinLinkedNpcController {

    @Inject(method = "loadNpcData", at = @At("RETURN"), remap = false)
    private void cnpcplus$loadScripts(EntityNPCInterface npc, CallbackInfo ci) {
        if (npc == null || npc.linkedData == null) return;
        if (!ExtraDataStorage.getBool(npc.linkedData)) return;
        if (npc.linkedData.data.contains("Scripts", 9)) {
            npc.script.load(npc.linkedData.data);
            npc.script.lastInited = -1L;
            npc.updateAI = true;
        }
    }

    @Inject(method = "readNpcData", at = @At("RETURN"), remap = false)
    private void cnpcplus$includeScripts(EntityNPCInterface npc,
                                         CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> cir) {
        if (npc == null || npc.linkedData == null) return;
        if (!ExtraDataStorage.getBool(npc.linkedData)) return;
        npc.script.save(cir.getReturnValue());
    }
}
