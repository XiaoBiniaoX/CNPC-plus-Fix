package bin.cnpcplus.recipe.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server -> client: whether syncId is in shared_recipes.dat */
public class PacketPersistState implements IMessage {
    private int syncId;
    private boolean persisted;

    public PacketPersistState() {}

    public PacketPersistState(int syncId, boolean persisted) {
        this.syncId = syncId;
        this.persisted = persisted;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        syncId = buf.readInt();
        persisted = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(syncId);
        buf.writeBoolean(persisted);
    }

    public static class Handler implements IMessageHandler<PacketPersistState, IMessage> {
        @Override
        public IMessage onMessage(PacketPersistState msg, MessageContext ctx) {
            // Always schedule on client main thread + refresh GUI
            PersistClientState.applyFromServer(msg.syncId, msg.persisted);
            return null;
        }
    }
}
