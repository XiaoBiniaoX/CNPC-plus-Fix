package top.cnpcplus.mixin;

import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketScriptSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = SPacketScriptSave.class, remap = false)
public class MixinSPacketScriptSaveLinked {

    @Shadow
    private int type;

    @Inject(method = "handle", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveLinkedScripts(CallbackInfo ci) {
        if (type != 0) return;
        var npc = ((PacketServerBasic) (Object) this).npc;
        if (npc == null || npc.linkedData == null) return;
        if (!ExtraDataStorage.getBool(npc.linkedData)) return;
        npc.script.save(npc.linkedData.data);
        npc.linkedData.time = System.currentTimeMillis();
        LinkedNpcController.Instance.save();
    }
}
