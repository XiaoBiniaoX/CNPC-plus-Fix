package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ExtraScriptHook;
import bin.cnpcplus.script.ScriptItemTimerAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import noppes.npcs.CustomItems;
import noppes.npcs.ScriptPlayerEventHandler;
import noppes.npcs.api.event.PlayerEvent;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerScriptData;
import noppes.npcs.items.ItemScripted;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScriptPlayerEventHandler.class, remap = false)
public abstract class MixinPlayerTickFix {
    @Inject(method = "onServerTick", at = @At("TAIL"), require = 1)
    private void cnpcplus$tickfix(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        PlayerScriptData handler = PlayerData.get(player).scriptData;
        handler.runScript(ExtraScriptHook.tickfix(), new PlayerEvent.UpdateEvent(handler.getPlayer()));
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.getItem() == CustomItems.scripted_item) ((ScriptItemTimerAccess)ItemScripted.GetWrapper(stack))
                    .cnpcplus$tickCarrier(handler.getPlayer(), handler.getPlayer().getWorld(), player.getServer().getTickCounter());
        }
    }
}
