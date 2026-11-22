package top.cnpcplus.script;

import java.util.Arrays;
import noppes.npcs.api.event.ItemEvent;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.item.IItemScripted;
import noppes.npcs.constants.EnumScriptType;

/** Native CNPC event types and carrier payloads. */
public final class ExtraScriptHook {
    private static class Registered {
        private static final EnumScriptType TICKFIX = EnumScriptType.valueOf("TICKFIX");
    }

    public static EnumScriptType tickfix() { return Registered.TICKFIX; }

    public static EnumScriptType[] register(EnumScriptType tickfix, EnumScriptType[] values) {
        EnumScriptType.npcScripts = append(EnumScriptType.npcScripts, tickfix);
        EnumScriptType.playerScripts = append(EnumScriptType.playerScripts, tickfix);
        EnumScriptType.blockScripts = append(EnumScriptType.blockScripts, tickfix);
        EnumScriptType.doorScripts = append(EnumScriptType.doorScripts, tickfix);
        EnumScriptType.itemScripts = append(append(EnumScriptType.itemScripts, EnumScriptType.TIMER), tickfix);
        return append(values, tickfix);
    }

    private static EnumScriptType[] append(EnumScriptType[] types, EnumScriptType added) {
        for (EnumScriptType type : types) if (type == added) return types;
        EnumScriptType[] result = Arrays.copyOf(types, types.length + 1);
        result[types.length] = added;
        return result;
    }

    public static class ItemTickEvent extends ItemEvent.UpdateEvent {
        public final IWorld world;

        public ItemTickEvent(IItemScripted item, IPlayer player, IWorld world) {
            super(item, player);
            this.world = world;
        }
    }

    public static final class ItemTimerEvent extends ItemTickEvent {
        public final int id;

        public ItemTimerEvent(IItemScripted item, IPlayer player, IWorld world, int id) {
            super(item, player, world);
            this.id = id;
        }
    }
}
