package bin.cnpcplus.recipe.network;

import bin.cnpcplus.recipe.RecipeControllerFacade;
import bin.cnpcplus.recipe.storage.SharedRecipeStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.controllers.data.RecipeCarpentry;

public class PacketRecipePersist implements IMessage {
    private int syncId;
    private boolean persist; // true=add to shared, false=remove from shared

    public PacketRecipePersist() {}

    public PacketRecipePersist(int syncId, boolean persist) {
        this.syncId = syncId;
        this.persist = persist;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        syncId = buf.readInt();
        persist = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(syncId);
        buf.writeBoolean(persist);
    }

    public static class Handler implements IMessageHandler<PacketRecipePersist, IMessage> {
        @Override
        public IMessage onMessage(final PacketRecipePersist msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    handle(player, msg.syncId, msg.persist);
                }
            });
            return null;
        }
    }

    private static void handle(EntityPlayerMP player, int syncId, boolean persist) {
        if (syncId <= 0) return;
        RecipeCarpentry recipe = RecipeControllerFacade.getRecipe(syncId);
        if (persist) {
            if (recipe == null) {
                player.sendMessage(new TextComponentString("[CNPCPlus] recipe not found id=" + syncId));
                return;
            }
            boolean ok = SharedRecipeStore.INSTANCE.persist(recipe);
            player.sendMessage(new TextComponentString(ok
                    ? "[CNPCPlus] persisted: " + recipe.name
                    : "[CNPCPlus] persist failed"));
        } else {
            String name = recipe != null ? recipe.name : null;
            boolean ok = SharedRecipeStore.INSTANCE.unpersist(syncId, name);
            player.sendMessage(new TextComponentString(ok
                    ? "[CNPCPlus] unpersisted" + (name != null ? (": " + name) : "")
                    : "[CNPCPlus] not in shared file"));
        }
        // Tell client updated flag
        CraftingViewNetworkBridge.sendPersistState(player, syncId,
                SharedRecipeStore.INSTANCE.isPersisted(syncId, recipe != null ? recipe.name : null));
    }
}
