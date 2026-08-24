package top.cnpcplus.craftingview.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;
import top.cnpcplus.persist.network.PacketPersistRecipe;
import top.cnpcplus.persist.network.PacketPersistStatus;
import top.cnpcplus.persist.network.PacketRequestPersistIds;
import top.cnpcplus.persist.network.PacketSyncPersistIds;
import top.cnpcplus.persist.network.PacketUnpersistRecipe;

import java.util.Optional;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "2";
    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "crafting_view"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    /**
     * 每个包都必须显式声明 NetworkDirection。
     *
     * <p>不声明 = 两个方向都接受。于是「只该由服务端下发」的包（Sync/Status/OpenGui）也会
     * 在服务端侧被注册为可接收；这类包的 handle 里走的是 DistExecutor 的客户端分支，
     * 在专用服务器上一旦被触发就会去解析客户端类。更要紧的是握手阶段两端的通道登记必须一致，
     * 方向缺失会让专用服务器与客户端对同一 id 的预期不一致，玩家进服时表现为「无效的数据包」被踢。
     */
    public static void init() {
        final NetworkDirection toServer = NetworkDirection.PLAY_TO_SERVER;
        final NetworkDirection toClient = NetworkDirection.PLAY_TO_CLIENT;
        int id = 0;
        CHANNEL.registerMessage(id++, PacketFillCraftingGrid.class,
                PacketFillCraftingGrid::encode,
                PacketFillCraftingGrid::decode,
                PacketFillCraftingGrid::handle,
                Optional.of(toServer));
        CHANNEL.registerMessage(id++, PacketPersistRecipe.class,
                PacketPersistRecipe::encode,
                PacketPersistRecipe::decode,
                PacketPersistRecipe::handle,
                Optional.of(toServer));
        CHANNEL.registerMessage(id++, PacketUnpersistRecipe.class,
                PacketUnpersistRecipe::encode,
                PacketUnpersistRecipe::decode,
                PacketUnpersistRecipe::handle,
                Optional.of(toServer));
        CHANNEL.registerMessage(id++, PacketRequestPersistIds.class,
                PacketRequestPersistIds::encode,
                PacketRequestPersistIds::decode,
                PacketRequestPersistIds::handle,
                Optional.of(toServer));
        CHANNEL.registerMessage(id++, PacketSyncPersistIds.class,
                PacketSyncPersistIds::encode,
                PacketSyncPersistIds::decode,
                PacketSyncPersistIds::handle,
                Optional.of(toClient));
        CHANNEL.registerMessage(id, PacketPersistStatus.class,
                PacketPersistStatus::encode,
                PacketPersistStatus::decode,
                PacketPersistStatus::handle,
                Optional.of(toClient));
    }
}
