package bin.cnpcplus.mixin.script;

import bin.cnpcplus.ai.NpcControlAccess;
import bin.cnpcplus.script.ExtraScriptHook;
import noppes.npcs.CustomNpcs;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcTickFix {
    @Inject(method = "func_70071_h_", at = @At("TAIL"), require = 1)
    private void cnpcplus$tickfix(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        if (!npc.world.isRemote && !npc.isDead && !npc.isKilled() && !CustomNpcs.FreezeNPCs
                && !((NpcControlAccess)npc).cnpcplus$isFrozen())
            npc.script.runScript(ExtraScriptHook.tickfix(), new NpcEvent.UpdateEvent(npc.wrappedNPC));
    }
}
