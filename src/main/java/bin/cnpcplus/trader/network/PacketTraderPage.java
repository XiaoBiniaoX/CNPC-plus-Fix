package bin.cnpcplus.trader.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.trader.TraderPager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.containers.ContainerNPCTraderSetup;

public record PacketTraderPage(int page, boolean delete) implements CustomPacketPayload {
    public static final Type<PacketTraderPage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "trader_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTraderPage> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PacketTraderPage::page,
                    ByteBufCodecs.BOOL,
                    PacketTraderPage::delete,
                    PacketTraderPage::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketTraderPage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof ContainerNPCTraderSetup menu) {
                if (msg.delete) {
                    if (TraderPager.removePage(menu.role, msg.page)) {
                        menu.role.toSave = true;
                    }
                } else {
                    if (msg.page > 0 && msg.page >= TraderPager.getPageCount(menu.role)) {
                        TraderPager.addPage(menu.role);
                    }
                    TraderPager.switchPage(menu.role, msg.page);
                }
                menu.broadcastChanges();
                PacketDistributor.sendToPlayer(player, new PacketTraderPageSync(TraderPager.getPage(menu.role)));
            } else if (player.containerMenu instanceof ContainerNPCTrader menu) {
                TraderPager.switchPage(menu.role, msg.page);
                menu.broadcastChanges();
                PacketDistributor.sendToPlayer(player, new PacketTraderPageSync(TraderPager.getPage(menu.role)));
            }
        });
    }
}
