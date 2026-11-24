package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ExtraScriptHook;
import noppes.npcs.EventHooks;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.item.IItemScripted;
import noppes.npcs.api.event.ItemEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = EventHooks.class, remap = false)
public abstract class MixinItemTickWorld {
    @Redirect(method = "onScriptItemUpdate", at = @At(value = "NEW", target = "noppes/npcs/api/event/ItemEvent$UpdateEvent"), require = 1)
    private static ItemEvent.UpdateEvent cnpcplus$world(IItemScripted item, IPlayer player) {
        return new ExtraScriptHook.ItemTickEvent(item, player, player.getWorld());
    }
}
