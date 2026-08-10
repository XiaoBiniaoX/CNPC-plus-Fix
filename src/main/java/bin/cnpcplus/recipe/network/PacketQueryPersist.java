package bin.cnpcplus.recipe.network;

import bin.cnpcplus.recipe.RecipeControllerFacade;
import bin.cnpcplus.recipe.storage.SharedRecipeStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.controllers.data.RecipeCarpentry;

public class PacketQueryPersist implements IMessage {
    private int syncId;

    public PacketQueryPersist() {}

    public PacketQueryPersist(int syncId) {
        this.syncId = syncId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        syncId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(syncId);
    }

    public static class Handler implements IMessageHandler<PacketQueryPersist, IMessage> {
        @Override
        public IMessage onMessage(final PacketQueryPersist msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    RecipeCarpentry r = RecipeControllerFacade.getRecipe(msg.syncId);
                    boolean p = SharedRecipeStore.INSTANCE.isPersisted(msg.syncId, r != null ? r.name : null);
                    CraftingViewNetworkBridge.sendPersistState(player, msg.syncId, p);
                }
            });
            return null;
        }
    }
}