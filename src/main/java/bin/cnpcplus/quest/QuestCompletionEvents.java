package bin.cnpcplus.quest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.controllers.data.PlayerData;

public class QuestCompletionEvents {
    private final Set<UUID> pendingPlayers = new HashSet<UUID>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntityPlayer().world.isRemote) {
            return;
        }
        this.pendingPlayers.add(event.getEntityPlayer().getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.START || this.pendingPlayers.isEmpty()) {
            return;
        }
        Set<UUID> players = new HashSet<UUID>(this.pendingPlayers);
        this.pendingPlayers.clear();
        net.minecraft.server.MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        for (UUID uuid : players) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(uuid);
            if (player != null) {
                PlayerData.get(player).questData.checkQuestCompletion(player, 0);
            }
        }
    }
}
