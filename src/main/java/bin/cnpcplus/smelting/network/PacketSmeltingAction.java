package bin.cnpcplus.smelting.network;

import bin.cnpcplus.smelting.ContainerSmeltingRecipes;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingGuiOpener;
import bin.cnpcplus.smelting.SmeltingPermissions;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Every client to server smelting request, distinguished by an action code.
 *
 * Items are never taken from this packet. The three stacks always come from the
 * server's own container, so a crafted packet cannot inject an ItemStack with
 * arbitrary NBT or an oversized count.
 */
public class PacketSmeltingAction implements IMessage {
    public static final int ACTION_OPEN = 0;
    public static final int ACTION_SAVE = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_REQUEST_LIST = 3;
    public static final int ACTION_SELECT = 4;
    public static final int ACTION_NEW = 5;

    private int action;
    private int id;
    private String name = "";
    private float cookTime = 200.0F;
    private float xp;
    private boolean blast;
    private boolean smoker;
    private boolean generic;

    public PacketSmeltingAction() {}

    public PacketSmeltingAction(int action, int id) {
        this.action = action;
        this.id = id;
    }

    public PacketSmeltingAction(int action, int id, String name, float cookTime, float xp,
                                boolean blast, boolean smoker, boolean generic) {
        this.action = action;
        this.id = id;
        this.name = name == null ? "" : name;
        this.cookTime = cookTime;
        this.xp = xp;
        this.blast = blast;
        this.smoker = smoker;
        this.generic = generic;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.id = buf.readInt();
        this.name = SmeltingRecipeData.clampName(ByteBufUtils.readUTF8String(buf));
        this.cookTime = SmeltingRecipeData.clampCookTime(buf.readFloat());
        this.xp = SmeltingRecipeData.clampXp(buf.readFloat());
        this.blast = buf.readBoolean();
        this.smoker = buf.readBoolean();
        this.generic = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        buf.writeInt(this.id);
        ByteBufUtils.writeUTF8String(buf, this.name == null ? "" : this.name);
        buf.writeFloat(this.cookTime);
        buf.writeFloat(this.xp);
        buf.writeBoolean(this.blast);
        buf.writeBoolean(this.smoker);
        buf.writeBoolean(this.generic);
    }

    public static class Handler implements IMessageHandler<PacketSmeltingAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketSmeltingAction msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) {
                return null;
            }
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    handle(msg, player);
                }
            });
            return null;
        }

        private static void handle(PacketSmeltingAction msg, EntityPlayerMP player) {
            if (!SmeltingPermissions.canEdit(player)) {
                return;
            }
            switch (msg.action) {
                case ACTION_OPEN:
                    SmeltingGuiOpener.open(player, msg.id);
                    return;
                case ACTION_SAVE:
                    save(msg, player);
                    break;
                case ACTION_REMOVE:
                    if (msg.id >= 0) {
                        SmeltingRecipeRegistry.remove(msg.id);
                        ContainerSmeltingRecipes container = container(player);
                        if (container != null) {
                            container.clearRecipe(-1);
                        }
                    }
                    break;
                case ACTION_REQUEST_LIST:
                    break;
                case ACTION_SELECT: {
                    ContainerSmeltingRecipes container = container(player);
                    if (container != null) {
                        container.setRecipe(SmeltingRecipeRegistry.get(msg.id));
                    }
                    break;
                }
                case ACTION_NEW: {
                    ContainerSmeltingRecipes container = container(player);
                    if (container != null) {
                        container.clearRecipe(-1);
                    }
                    break;
                }
                default:
                    return;
            }
            SmeltingSync.sendTo(player);
            if (msg.action == ACTION_SAVE || msg.action == ACTION_REMOVE) {
                SmeltingSync.sendToAll(player.getServer());
            }
        }

        private static void save(PacketSmeltingAction msg, EntityPlayerMP player) {
            ContainerSmeltingRecipes container = container(player);
            // Without the editor open there is no authoritative source for the
            // three stacks, and trusting the packet would mean trusting client NBT.
            if (container == null) {
                return;
            }
            SmeltingRecipeData data = new SmeltingRecipeData();
            data.id = msg.id;
            data.name = resolveName(msg.name, msg.id);
            data.input = container.getInput().copy();
            data.fuel = container.getFuel().copy();
            data.output = container.getOutput().copy();
            data.cookTime = SmeltingRecipeData.clampCookTime(msg.cookTime);
            data.xp = SmeltingRecipeData.clampXp(msg.xp);
            data.blastAllowed = msg.blast;
            data.smokerAllowed = msg.smoker;
            data.genericFuelAllowed = msg.generic;
            if (data.input.isEmpty() || data.output.isEmpty()) {
                return;
            }
            if (data.id >= 0) {
                SmeltingRecipeRegistry.update(data);
            } else {
                SmeltingRecipeData created = SmeltingRecipeRegistry.create(data);
                if (created != null) {
                    container.setRecipe(created);
                }
            }
        }

        private static String resolveName(String raw, int id) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                name = "new";
            }
            if ("new".equals(name) && id < 0) {
                name = "new" + System.currentTimeMillis() % 10000L;
            }
            return SmeltingRecipeData.clampName(name);
        }

        private static ContainerSmeltingRecipes container(EntityPlayerMP player) {
            Container open = player.openContainer;
            return open instanceof ContainerSmeltingRecipes ? (ContainerSmeltingRecipes) open : null;
        }
    }
}
