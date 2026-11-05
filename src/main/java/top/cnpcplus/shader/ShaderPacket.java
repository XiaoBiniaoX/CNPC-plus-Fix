package top.cnpcplus.shader;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public record ShaderPacket(CompoundTag data) {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("cnpcplus:shaders"), () -> "1", "1"::equals, "1"::equals);
    public static void init() {
        CHANNEL.registerMessage(0, ShaderPacket.class, ShaderPacket::encode, ShaderPacket::decode, ShaderPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, ShaderCatalogPacket.class, ShaderCatalogPacket::encode, ShaderCatalogPacket::decode, ShaderCatalogPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void send(ServerPlayer player, CompoundTag data) {
        if (data.sizeInBytes() > 16384) throw new IllegalArgumentException("Shader packet exceeds 16 KiB");
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShaderPacket(data));
    }
    public void encode(FriendlyByteBuf buffer) { buffer.writeNbt(data); }
    public static ShaderPacket decode(FriendlyByteBuf buffer) { return new ShaderPacket(buffer.readNbt(new net.minecraft.nbt.NbtAccounter(16384))); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.accept(data)));
        supplier.get().setPacketHandled(true);
    }
}
