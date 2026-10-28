package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.invpage.network.PacketNpcInvPage;
import bin.cnpcplus.follower.network.PacketFollowerDismiss;
import bin.cnpcplus.smelting.network.PacketSmeltingAction;
import bin.cnpcplus.smelting.network.PacketSmeltingSync;
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
        CHANNEL.registerMessage(PacketNpcInvPage.Handler.class, PacketNpcInvPage.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketTraderPage.Handler.class, PacketTraderPage.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketTraderPageSync.Handler.class, PacketTraderPageSync.class, nextId++, Side.CLIENT);
        CHANNEL.registerMessage(PacketFollowerDismiss.Handler.class, PacketFollowerDismiss.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketMountControlInput.Handler.class, PacketMountControlInput.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketSmeltingAction.Handler.class, PacketSmeltingAction.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(PacketSmeltingSync.Handler.class, PacketSmeltingSync.class, nextId++, Side.CLIENT);
        CHANNEL.registerMessage(bin.cnpcplus.common.PacketLinkedScriptSync.Handler.class, bin.cnpcplus.common.PacketLinkedScriptSync.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(bin.cnpcplus.common.PacketLinkedScriptSync.ClientHandler.class, bin.cnpcplus.common.PacketLinkedScriptSync.class, nextId++, Side.CLIENT);
        CHANNEL.registerMessage(bin.cnpcplus.crafting.network.PacketRecipeAction.Handler.class, bin.cnpcplus.crafting.network.PacketRecipeAction.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(bin.cnpcplus.crafting.network.PacketRecipeState.Handler.class, bin.cnpcplus.crafting.network.PacketRecipeState.class, nextId++, Side.CLIENT);
        CHANNEL.registerMessage(bin.cnpcplus.crafting.network.PacketRecipeUpload.Handler.class, bin.cnpcplus.crafting.network.PacketRecipeUpload.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(bin.cnpcplus.spawner.PacketSpawnerAction.Handler.class, bin.cnpcplus.spawner.PacketSpawnerAction.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(bin.cnpcplus.villagertrade.PacketVillageAction.Handler.class, bin.cnpcplus.villagertrade.PacketVillageAction.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(bin.cnpcplus.villagertrade.PacketVillageState.Handler.class, bin.cnpcplus.villagertrade.PacketVillageState.class, nextId++, Side.CLIENT);
        CHANNEL.registerMessage(bin.cnpcplus.transport.PacketTransportMeta.Handler.class, bin.cnpcplus.transport.PacketTransportMeta.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(bin.cnpcplus.transport.PacketTransportMeta.ClientHandler.class, bin.cnpcplus.transport.PacketTransportMeta.class, nextId++, Side.CLIENT);
        CHANNEL.registerMessage(bin.cnpcplus.transport.PacketTransportImage.Handler.class, bin.cnpcplus.transport.PacketTransportImage.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(bin.cnpcplus.transport.PacketTransportImage.ClientHandler.class, bin.cnpcplus.transport.PacketTransportImage.class, nextId++, Side.CLIENT);
    }
}
