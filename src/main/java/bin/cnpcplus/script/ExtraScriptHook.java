package bin.cnpcplus.script;

import net.minecraftforge.common.util.EnumHelper;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.event.ItemEvent;
import noppes.npcs.api.item.IItemScripted;
import noppes.npcs.constants.EnumScriptType;

public final class ExtraScriptHook {
    private static final EnumScriptType TICKFIX = EnumHelper.addEnum(EnumScriptType.class, "TICKFIX", new Class<?>[]{String.class}, "tickfix");
    private ExtraScriptHook() { }
    public static EnumScriptType tickfix() { return TICKFIX; }
    public static class ItemTickEvent extends ItemEvent.UpdateEvent {
        public final IWorld world;
        public ItemTickEvent(IItemScripted item, IPlayer player, IWorld world) {
            super(item, player); this.world = world;
        }
    }
    public static final class ItemTimerEvent extends ItemTickEvent {
        public final int id;
        public ItemTimerEvent(IItemScripted item, IPlayer player, IWorld world, int id) {
            super(item, player, world); this.id = id;
        }
    }
}
