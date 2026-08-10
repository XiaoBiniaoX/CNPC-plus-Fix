package bin.cnpcplus.trader.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.trader.TraderPager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.containers.ContainerNPCTraderSetup;
import noppes.npcs.roles.RoleTrader;
import noppes.npcs.shared.client.gui.components.GuiBasicContainer;

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
            if (ctx.player() == null) return;
            RoleTrader role = cnpcplus$roleOf(ctx.player().containerMenu);
            if (role == null) return;
            TraderPager.setPageOnly(role, msg.page());
            if (Minecraft.getInstance().screen instanceof GuiBasicContainer gui
                    && gui.getMenu() == ctx.player().containerMenu) {
                gui.init();
            }
        });
    }

    private static RoleTrader cnpcplus$roleOf(Object menu) {
        if (menu instanceof ContainerNPCTrader c) return c.role;
        if (menu instanceof ContainerNPCTraderSetup s) return s.role;
        return null;
    }
}