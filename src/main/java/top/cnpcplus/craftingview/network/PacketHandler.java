package top.cnpcplus.craftingview.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;
import top.cnpcplus.persist.network.PacketPersistRecipe;
import top.cnpcplus.persist.network.PacketPersistStatus;
import top.cnpcplus.persist.network.PacketRequestPersistIds;
import top.cnpcplus.persist.network.PacketSyncPersistIds;
import top.cnpcplus.persist.network.PacketUnpersistRecipe;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "2";
    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "crafting_view"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++, PacketFillCraftingGrid.class,
                PacketFillCraftingGrid::encode,
                PacketFillCraftingGrid::decode,
                PacketFillCraftingGrid::handle);
        CHANNEL.registerMessage(id++, PacketPersistRecipe.class,
                PacketPersistRecipe::encode,
                PacketPersistRecipe::decode,
                PacketPersistRecipe::handle);
        CHANNEL.registerMessage(id++, PacketUnpersistRecipe.class,
                PacketUnpersistRecipe::encode,
                PacketUnpersistRecipe::decode,
                PacketUnpersistRecipe::handle);
        CHANNEL.registerMessage(id++, PacketRequestPersistIds.class,
                PacketRequestPersistIds::encode,
                PacketRequestPersistIds::decode,
                PacketRequestPersistIds::handle);
        CHANNEL.registerMessage(id++, PacketSyncPersistIds.class,
                PacketSyncPersistIds::encode,
                PacketSyncPersistIds::decode,
                PacketSyncPersistIds::handle);
        CHANNEL.registerMessage(id, PacketPersistStatus.class,
                PacketPersistStatus::encode,
                PacketPersistStatus::decode,
                PacketPersistStatus::handle);
    }
}
