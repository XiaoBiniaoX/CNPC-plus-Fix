package bin.cnpcplus.crafting.network;

import bin.cnpcplus.crafting.CraftKind;
import bin.cnpcplus.crafting.CustomRecipe;
import bin.cnpcplus.crafting.RecipeContainer;
import bin.cnpcplus.crafting.RecipeStore;
import bin.cnpcplus.smelting.SmeltingGuiOpener;
import bin.cnpcplus.smelting.SmeltingPermissions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class PacketRecipeAction implements IMessage {
    public static final int OPEN = 0, NEW = 1, SELECT = 2, SAVE = 3, REMOVE = 4;
    private int action, kind, window;
    private NBTTagCompound data = new NBTTagCompound();
    public PacketRecipeAction() { }
    public PacketRecipeAction(int action, CraftKind kind, int window, NBTTagCompound data) {
        this.action = action; this.kind = kind.ordinal(); this.window = window; this.data = data;
    }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(action); buf.writeInt(kind); buf.writeInt(window); ByteBufUtils.writeTag(buf, data); }
    @Override public void fromBytes(ByteBuf buf) { action = buf.readInt(); kind = buf.readInt(); window = buf.readInt(); data = ByteBufUtils.readTag(buf); }

    public static final class Handler implements IMessageHandler<PacketRecipeAction, IMessage> {
        @Override public IMessage onMessage(PacketRecipeAction message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(message, player));
            return null;
        }

        static void handle(PacketRecipeAction message, EntityPlayerMP player) {
            if (message.kind < 0 || message.kind >= CraftKind.values().length || message.data == null
                    || !SmeltingPermissions.canEdit(player)) return;
            CraftKind kind = CraftKind.values()[message.kind];
            if (message.action == OPEN) { SmeltingGuiOpener.openRecipe(player, kind); return; }
            if (!(player.openContainer instanceof RecipeContainer) || player.openContainer.windowId != message.window) return;
            RecipeContainer container = (RecipeContainer) player.openContainer;
            if (container.kind != kind) return;
            int id = message.data.getInteger("Id");
            switch (message.action) {
                case NEW: container.select(null); break;
                case SELECT: container.select(RecipeStore.get(kind, id)); break;
                case SAVE:
                    if (id != container.recipe.id) return;
                    CustomRecipe recipe = CustomRecipe.fromNBT(kind, message.data);
                    container.readSlots(recipe);
                    if (recipe.name.trim().isEmpty()) recipe.name = "new";
                    CustomRecipe saved = RecipeStore.save(recipe);
                    if (saved != null) container.select(saved);
                    break;
                case REMOVE:
                    if (id < 0 || id != container.recipe.id) return;
                    RecipeStore.remove(kind, id);
                    container.select(null);
                    break;
                default: return;
            }
            PacketRecipeState.send(player, container);
        }
    }
}
