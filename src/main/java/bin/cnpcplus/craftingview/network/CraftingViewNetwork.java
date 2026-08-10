package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.network.PacketPersistState;
import bin.cnpcplus.recipe.network.PacketQueryPersist;
import bin.cnpcplus.recipe.network.PacketRecipePersist;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class CraftingViewNetwork {
    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(CnpcPlus.MODID);

    private static int nextId;

    private CraftingViewNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(PacketFillCraftingGrid.Handler.class, PacketFillCraftingGrid.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketRecipePersist.Handler.class, PacketRecipePersist.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketQueryPersist.Handler.class, PacketQueryPersist.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketPersistState.Handler.class, PacketPersistState.class, nextId++, Side.CLIENT);
    }
}
