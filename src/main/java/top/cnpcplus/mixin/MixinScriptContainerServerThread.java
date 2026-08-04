package top.cnpcplus.mixin;

import net.minecraft.server.MinecraftServer;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge chat (and some other) events run CNPC scripts off the server thread while holding
 * ScriptContainer's global lock. World APIs (setPosition → chunk load) then wait for the server
 * thread, which itself waits on the same lock for NPC tick scripts → freeze.
 *
 * Re-dispatch off-thread runs onto the server thread and wait (without holding cnpcslock).
 */
@Mixin(value = ScriptContainer.class, remap = false)
public class MixinScriptContainerServerThread {

    @Inject(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$runOnServerThread(String type, Object event, CallbackInfo ci) {
        MinecraftServer server = CustomNpcs.Server;
        if (server == null || server.isSameThread()) return;

        ScriptContainer self = (ScriptContainer) (Object) this;
        try {
            server.submit(() -> self.run(type, event)).join();
        } catch (Exception e) {
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
        ci.cancel();
    }
}
