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
            // 页号来自客户端，先做边界校验再使用：负数直接丢弃，上限同 TraderPager.MAX_PAGES。
            // 不校验会让恶意客户端用超大页号驱动 addPage 无限扩张页表（每页 36+18 槽位）造成内存放大。
            if (msg.page < 0 || msg.page >= TraderPager.MAX_PAGES) return;
            if (player.containerMenu instanceof ContainerNPCTraderSetup menu) {
                // Setup 是管理界面，必须要求 OP 权限；仅靠「菜单已打开」不足以证明有编辑权。
                if (!player.hasPermissions(2)) return;
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
