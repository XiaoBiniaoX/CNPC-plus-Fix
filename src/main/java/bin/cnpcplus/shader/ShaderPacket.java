package bin.cnpcplus.shader;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.*;

public final class ShaderPacket implements IMessage {
    private NBTTagCompound data;
    public ShaderPacket() {} public ShaderPacket(NBTTagCompound data) { this.data = data; }
    public static void send(EntityPlayerMP player, NBTTagCompound data) { CraftingViewNetwork.CHANNEL.sendTo(new ShaderPacket(data), player); }
    @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, data); }
    @Override public void fromBytes(ByteBuf buf) { data = ByteBufUtils.readTag(buf); if (data == null) throw new IllegalArgumentException("Missing shader state"); }
    public static final class Handler implements IMessageHandler<ShaderPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(ShaderPacket msg, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> bin.cnpcplus.shader.client.ClientShaders.accept(msg.data)); return null;
        }
    }
}
