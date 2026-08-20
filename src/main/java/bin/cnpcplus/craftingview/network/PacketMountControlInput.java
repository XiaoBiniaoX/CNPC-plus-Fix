package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.common.IMountControlData;
import bin.cnpcplus.common.IMountControlInput;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.entity.EntityNPCInterface;

public class PacketMountControlInput implements IMessage {
    private float strafe;
    private float forward;
    private boolean jump;
    private boolean sneak;

    public PacketMountControlInput() {}

    public PacketMountControlInput(float strafe, float forward, boolean jump, boolean sneak) {
        this.strafe = strafe;
        this.forward = forward;
        this.jump = jump;
        this.sneak = sneak;
    }

    @Override public void fromBytes(ByteBuf buf) {
        this.strafe = buf.readFloat();
        this.forward = buf.readFloat();
        this.jump = buf.readBoolean();
        this.sneak = buf.readBoolean();
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeFloat(this.strafe);
        buf.writeFloat(this.forward);
        buf.writeBoolean(this.jump);
        buf.writeBoolean(this.sneak);
    }

    public static class Handler implements IMessageHandler<PacketMountControlInput, IMessage> {
        @Override public IMessage onMessage(final PacketMountControlInput msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() { apply(player, msg); }
            });
            return null;
        }
    }

    private static void apply(EntityPlayerMP player, PacketMountControlInput msg) {
        Entity riding = player.getRidingEntity();
        if (!(riding instanceof EntityNPCInterface) || riding.world != player.world
                || !player.isEntityAlive() || !riding.isEntityAlive()) return;
        EntityNPCInterface npc = (EntityNPCInterface) riding;
        if (npc.ais == null) return;
        if (!((IMountControlData) (Object) npc.ais).cnpcplus$getMountControl()) return;
        if (!Float.isFinite(msg.strafe) || !Float.isFinite(msg.forward)) return;

        float strafe = clamp(msg.strafe, -1.0f, 1.0f) * 0.25f;
        float forward = clamp(msg.forward, -1.0f, 1.0f) * 0.25f;
        ((IMountControlInput) (Object) npc).cnpcplus$setMountInput(strafe, forward, msg.jump, msg.sneak);
        npc.rotationYaw = player.rotationYaw;
        npc.prevRotationYaw = npc.rotationYaw;
        npc.renderYawOffset = npc.rotationYaw;
        npc.rotationYawHead = npc.rotationYaw;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
