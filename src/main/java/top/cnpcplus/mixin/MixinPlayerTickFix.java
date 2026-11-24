package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import noppes.npcs.CustomItems;
import noppes.npcs.ScriptPlayerEventHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.event.PlayerEvent;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.items.ItemScripted;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.script.ExtraScriptHook;
import top.cnpcplus.script.ScriptItemTimerAccess;

@Mixin(value = ScriptPlayerEventHandler.class, remap = false)
public abstract class MixinPlayerTickFix {
    @Inject(method = "onServerTick", at = @At("TAIL"))
    private void cnpcplus$tickFix(PlayerTickEvent.Pre event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var handler = PlayerData.get(player).scriptData;
        if (handler.isEnabled()) handler.runScript(ExtraScriptHook.tickfix(), new PlayerEvent.UpdateEvent(handler.getPlayer()));
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(CustomItems.scripted_item)) {
                ((ScriptItemTimerAccess) ItemScripted.GetWrapper(stack)).cnpcplus$tickCarrier(
                        handler.getPlayer(), NpcAPI.Instance().getIWorld(player.serverLevel()), player.server.getTickCount());
            }
        }
    }
}
