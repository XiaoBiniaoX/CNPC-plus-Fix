package top.cnpcplus.mixin;

import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.script.ExtraScriptHook;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcTickFix {
    @Inject(method = "m_8119_", at = @At("TAIL"))
    private void cnpcplus$tickFix(CallbackInfo ci) {
        var npc = (EntityNPCInterface) (Object) this;
        if (!npc.isClientSide() && !npc.isRemoved() && npc.script.getEnabled()) {
            npc.script.runScript(ExtraScriptHook.tickfix(), new NpcEvent.UpdateEvent(npc.wrappedNPC));
        }
    }
}
