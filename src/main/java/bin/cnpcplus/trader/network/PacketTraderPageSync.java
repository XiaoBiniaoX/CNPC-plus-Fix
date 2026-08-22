package bin.cnpcplus.trader.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.trader.TraderPager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketTraderPageSync(int page) implements CustomPacketPayload {
    public static final Type<PacketTraderPageSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "trader_page_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTraderPageSync> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PacketTraderPageSync::page, PacketTraderPageSync::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketTraderPageSync msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 页号来自网络，先做边界校验；实际客户端处理放在 TraderPageSyncClient，
            // 本类不得直接引用客户端类型（服务端会构造本类来下发）。
            if (msg.page() < 0 || msg.page() >= TraderPager.MAX_PAGES) return;
            bin.cnpcplus.trader.client.TraderPageSyncClient.apply(ctx.player(), msg.page());
        });
    }
}
