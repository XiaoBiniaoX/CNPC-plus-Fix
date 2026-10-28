package bin.cnpcplus.villagertrade;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.List;

public final class PacketVillageAction implements IMessage {
    public static final int OPEN_EDIT = 0, SELECT = 1, ADD = 2, REMOVE = 3, SETTINGS = 4;
    private int action, window, selected;
    private NBTTagCompound data = new NBTTagCompound();
    public PacketVillageAction() { }
    public PacketVillageAction(int action, int window, int selected, NBTTagCompound data) { this.action = action; this.window = window; this.selected = selected; this.data = data; }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(action); buf.writeInt(window); buf.writeInt(selected); ByteBufUtils.writeTag(buf, data); }
    @Override public void fromBytes(ByteBuf buf) { action = buf.readInt(); window = buf.readInt(); selected = buf.readInt(); data = ByteBufUtils.readTag(buf); }

    public static final class Handler implements IMessageHandler<PacketVillageAction, IMessage> {
        @Override public IMessage onMessage(PacketVillageAction message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(message, player)); return null;
        }
        private static void handle(PacketVillageAction message, EntityPlayerMP player) {
            if (message.action == OPEN_EDIT) {
                EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
                if (npc != null && npc.getEntityId() == message.selected && npc.roleInterface instanceof RoleVillagerTrader
                        && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_ADVANCED)) {
                    bin.cnpcplus.smelting.SmeltingGuiOpener.openVillager(player, npc, true);
                }
                return;
            }
            if (!(player.openContainer instanceof VillagerContainer) || player.openContainer.windowId != message.window) return;
            VillagerContainer container = (VillagerContainer) player.openContainer;
            if (!container.canInteractWith(player)) return;
            if (message.action == SELECT) { container.select(message.selected); return; }
            if (!container.editing || message.data == null) return;
            List<TradeData> trades = TradeStore.list(container.role.tradeListId);
            if (message.action == ADD && trades.size() < 64) {
                trades.add(new TradeData()); TradeStore.save(container.role.tradeListId, trades); container.select(trades.size() - 1);
            } else if (message.action == REMOVE && container.selected >= 0 && container.selected < trades.size() && container.selected == message.selected) {
                trades.remove(container.selected); TradeStore.save(container.role.tradeListId, trades); container.select(Math.min(container.selected, trades.size() - 1));
            } else if (message.action == SETTINGS && container.selected >= 0 && container.selected < trades.size() && container.selected == message.selected) {
                TradeData trade = trades.get(container.selected);
                trade.limit = message.data.getBoolean("LimitOn"); trade.nameOnly = message.data.getBoolean("NameOnly");
                trade.limitCount = Math.max(1, Math.min(10000, message.data.getInteger("LimitCount")));
                double seconds = message.data.getDouble("Refresh");
                trade.refreshSeconds = Double.isFinite(seconds) ? Math.max(0, Math.min(99999, seconds)) : 0;
                if (!trade.limit) { trade.uses = 0; trade.resetAt = 0; }
                else trade.refresh(System.currentTimeMillis());
                TradeStore.saveRow(container.role.tradeListId, container.selected, trade); container.sync();
            }
        }
    }
}
