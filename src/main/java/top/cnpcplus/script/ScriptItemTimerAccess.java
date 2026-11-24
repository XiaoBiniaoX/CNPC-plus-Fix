package top.cnpcplus.script;

import noppes.npcs.api.ITimers;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IPlayer;

public interface ScriptItemTimerAccess {
    ITimers getTimers();
    void cnpcplus$tickCarrier(IPlayer player, IWorld world, int serverTick);
}
