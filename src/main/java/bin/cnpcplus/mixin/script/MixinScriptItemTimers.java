package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.*;
import noppes.npcs.api.ITimers;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import noppes.npcs.constants.EnumScriptType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemScriptedWrapper.class, remap = false)
public abstract class MixinScriptItemTimers implements ScriptItemTimerAccess {
    @Unique private ScriptItemTimers cnpcplus$timers;
    @Unique private int cnpcplus$lastTick = -1;
    @Shadow public long lastInited;
    @Inject(method = "setScriptNBT", at = @At("HEAD"), require = 1)
    private void cnpcplus$clearEditedTimers(net.minecraft.nbt.NBTTagCompound data, CallbackInfo ci) {
        if (data.hasKey("Scripts")) cnpcplus$timers = null;
    }
    @Inject(method = "runScript", at = @At("HEAD"), require = 1)
    private void cnpcplus$clearReloadedTimers(EnumScriptType type, net.minecraftforge.fml.common.eventhandler.Event event, CallbackInfo ci) {
        if (noppes.npcs.controllers.ScriptController.Instance.lastLoaded > lastInited) cnpcplus$timers = null;
    }
    public ITimers getTimers() {
        if (cnpcplus$timers == null) cnpcplus$timers = new ScriptItemTimers();
        return cnpcplus$timers;
    }
    public void cnpcplus$tickCarrier(IPlayer player, IWorld world, int tick) {
        if (cnpcplus$lastTick == tick) return;
        cnpcplus$lastTick = tick;
        ItemScriptedWrapper item = (ItemScriptedWrapper)(Object)this;
        item.runScript(ExtraScriptHook.tickfix(), new ExtraScriptHook.ItemTickEvent(item, player, world));
        if (!item.getEnabled()) return;
        if (cnpcplus$timers != null) cnpcplus$timers.tick(id ->
                item.runScript(EnumScriptType.TIMER, new ExtraScriptHook.ItemTimerEvent(item, player, world, id)));
    }
}
