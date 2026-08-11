package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.invpage.network.PacketNpcInvPage;
import bin.cnpcplus.recipe.network.PacketPersistState;
import bin.cnpcplus.recipe.network.PacketQueryPersist;
import bin.cnpcplus.recipe.network.PacketRecipePersist;
import bin.cnpcplus.trader.network.PacketTraderPage;
import bin.cnpcplus.trader.network.PacketTraderPageSync;
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
        CHANNEL.registerMessage(PacketNpcInvPage.Handler.class, PacketNpcInvPage.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketTraderPage.Handler.class, PacketTraderPage.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketTraderPageSync.Handler.class, PacketTraderPageSync.class, nextId++, Side.CLIENT);
    }
}
