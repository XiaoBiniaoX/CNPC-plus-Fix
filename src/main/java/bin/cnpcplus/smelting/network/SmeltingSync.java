package bin.cnpcplus.smelting.network;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Server side helper for pushing the recipe list out.
 *
 * Only players who may edit receive the list: it is editor data, and answering
 * every client would both leak recipe contents and let anyone make the server
 * serialise the whole list on demand.
 */
public final class SmeltingSync {
    private SmeltingSync() {}

    public static void sendTo(EntityPlayerMP player) {
        sendTo(player, currentSelection(player));
    }

    public static void sendTo(EntityPlayerMP player, int selectedId) {
        if (player == null || !bin.cnpcplus.smelting.SmeltingPermissions.canEdit(player)) {
            return;
        }
        CraftingViewNetwork.CHANNEL.sendTo(
                new PacketSmeltingSync(SmeltingRecipeRegistry.list(), selectedId), player);
    }

    /** After a change, refresh everyone who currently has the editor open. */
    public static void sendToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); ++i) {
            EntityPlayerMP player = players.get(i);
            if (player != null
                    && player.openContainer instanceof bin.cnpcplus.smelting.ContainerSmeltingRecipes) {
                sendTo(player);
            }
        }
    }

    private static int currentSelection(EntityPlayerMP player) {
        if (player != null && player.openContainer
                instanceof bin.cnpcplus.smelting.ContainerSmeltingRecipes) {
            return ((bin.cnpcplus.smelting.ContainerSmeltingRecipes) player.openContainer)
                    .getSelectedId();
        }
        return -1;
    }
}
