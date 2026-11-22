package top.cnpcplus.mixin;

import noppes.npcs.CustomNpcs;
import noppes.npcs.api.ITimers;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import noppes.npcs.controllers.ScriptController;
import noppes.npcs.constants.EnumScriptType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcplus.script.ExtraScriptHook;
import top.cnpcplus.script.ScriptItemTimerAccess;
import top.cnpcplus.script.ScriptItemTimers;

@Mixin(value = ItemScriptedWrapper.class, remap = false)
public abstract class MixinScriptItemTimers implements ScriptItemTimerAccess {
    @Unique private ScriptItemTimers cnpcplus$timers;
    @Unique private int cnpcplus$lastTick = -1;

    @Override public ITimers getTimers() {
        if (cnpcplus$timers == null) cnpcplus$timers = new ScriptItemTimers();
        return cnpcplus$timers;
    }

    @Override public void cnpcplus$tickCarrier(IPlayer player, IWorld world, int serverTick) {
        var item = (ItemScriptedWrapper) (Object) this;
        if (!CustomNpcs.EnableScripting || !ScriptController.HasStart || (item.loaded && !item.enabled)
                || cnpcplus$lastTick == serverTick) return;
        cnpcplus$lastTick = serverTick;
        item.runScript(ExtraScriptHook.tickfix(), new ExtraScriptHook.ItemTickEvent(item, player, world));
        if (cnpcplus$timers != null) {
            cnpcplus$timers.tick(id -> item.runScript(EnumScriptType.TIMER, new ExtraScriptHook.ItemTimerEvent(item, player, world, id)));
        }
    }
}
