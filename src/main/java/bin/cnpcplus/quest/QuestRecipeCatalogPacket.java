package bin.cnpcplus.quest;

import bin.cnpcplus.crafting.CraftKind;
import bin.cnpcplus.crafting.CustomRecipe;
import bin.cnpcplus.crafting.RecipeStore;
import bin.cnpcplus.craftingview.RecipeAccess;
import bin.cnpcplus.craftingview.RecipeView;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;
import bin.cnpcplus.station.StationRecipe;
import bin.cnpcplus.station.StationRecipes;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.CustomNpcsPermissions;

/** Read-only recipe snapshots for quest editing; never opens or changes a container. */
public final class QuestRecipeCatalogPacket implements IMessage {
    private int station;
    private boolean append;
    private NBTTagCompound data = new NBTTagCompound();
    public QuestRecipeCatalogPacket() {}
    public QuestRecipeCatalogPacket(CraftStation station) { this.station = station.ordinal(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(station); buf.writeBoolean(append); ByteBufUtils.writeTag(buf, data); }
    @Override public void fromBytes(ByteBuf buf) { station = buf.readInt(); append = buf.readBoolean(); data = ByteBufUtils.readTag(buf); }

    public static final class Handler implements IMessageHandler<QuestRecipeCatalogPacket, IMessage> {
        @Override public IMessage onMessage(QuestRecipeCatalogPacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (packet.station < 0 || packet.station >= CraftStation.values().length
                        || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.GLOBAL_QUEST)) return;
                List<NBTTagCompound> recipes = recipes(CraftStation.values()[packet.station]);
                // Bound individual packets below Forge's NBT packet limit.
                for (int from = 0; from < Math.max(1, recipes.size()); from += 64) {
                    QuestRecipeCatalogPacket reply = new QuestRecipeCatalogPacket(CraftStation.values()[packet.station]);
                    reply.append = from > 0;
                    NBTTagList list = new NBTTagList();
                    for (int i = from; i < Math.min(from + 64, recipes.size()); i++) list.appendTag(recipes.get(i));
                    reply.data.setTag("Recipes", list);
                    CraftingViewNetwork.CHANNEL.sendTo(reply, player);
                }
            });
            return null;
        }
    }

    public static final class ClientHandler implements IMessageHandler<QuestRecipeCatalogPacket, IMessage> {
        @SideOnly(Side.CLIENT) @Override public IMessage onMessage(QuestRecipeCatalogPacket packet, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                if (packet.station >= 0 && packet.station < CraftStation.values().length && packet.data != null)
                    bin.cnpcplus.client.gui.QuestCraftRecipeCatalog.receive(CraftStation.values()[packet.station],
                            packet.append, packet.data.getTagList("Recipes", 10));
            });
            return null;
        }
    }

    private static List<NBTTagCompound> recipes(CraftStation station) {
        List<NBTTagCompound> result = new ArrayList<>();
        if (station == CraftStation.WORKBENCH || station == CraftStation.CARPENTRY) {
            int columns = station == CraftStation.WORKBENCH ? 3 : 4;
            for (RecipeView recipe : station == CraftStation.WORKBENCH ? RecipeAccess.getGlobalRecipes() : RecipeAccess.getAnvilRecipes()) {
                List<ItemStack> grid = new ArrayList<>(java.util.Collections.nCopies(columns * columns, ItemStack.EMPTY));
                for (int y = 0; y < recipe.recipeHeight; y++) for (int x = 0; x < recipe.recipeWidth; x++) {
                    grid.set((y + recipe.offsetY) * columns + x + recipe.offsetX,
                            recipe.getIngredient(y * recipe.recipeWidth + x));
                }
                add(result, Integer.toString(recipe.id), recipe.name, columns, Arrays.asList(recipe.output), grid);
            }
        } else if (station == CraftStation.SMITHING || station == CraftStation.ANVIL || station == CraftStation.DROP) {
            CraftKind kind = station == CraftStation.SMITHING ? CraftKind.SMITHING : station == CraftStation.ANVIL ? CraftKind.ANVIL : CraftKind.DROP;
            for (CustomRecipe recipe : RecipeStore.list(kind))
                add(result, Integer.toString(recipe.id), recipe.name, kind == CraftKind.DROP ? 3 : recipe.inputs.length,
                        Arrays.asList(recipe.result), Arrays.asList(recipe.inputs));
        } else if (station == CraftStation.FURNACE) {
            for (SmeltingRecipeData recipe : SmeltingRecipeRegistry.list()) {
                if (recipe.output.isEmpty()) continue;
                add(result, Integer.toString(recipe.id), recipe.name, 1, Arrays.asList(recipe.output), Arrays.asList(recipe.input, recipe.fuel));
                result.get(result.size() - 1).setBoolean("GenericFuel", recipe.genericFuelAllowed);
            }
        } else {
            int kind = station == CraftStation.GRINDSTONE ? 0 : station == CraftStation.BREWING ? 1 : 2;
            for (StationRecipe recipe : StationRecipes.catalogue(kind).values())
                add(result, Integer.toString(recipe.id), recipe.name, kind == 1 ? 3 : kind == 0 ? 2 : 1,
                        kind == 1 ? Arrays.asList(recipe.outputs) : Arrays.asList(recipe.outputs[0]),
                        Arrays.asList(Arrays.copyOf(recipe.inputs, kind == 1 ? 4 : kind == 0 ? 2 : 1)));
        }
        return result;
    }

    private static void add(List<NBTTagCompound> list, String id, String name, int columns,
                            List<ItemStack> outputs, List<ItemStack> inputs) {
        if (outputs.stream().allMatch(ItemStack::isEmpty)) return;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Id", id); tag.setString("Name", name == null ? "" : name); tag.setInteger("Columns", columns);
        NBTTagList in = new NBTTagList(), out = new NBTTagList();
        for (ItemStack stack : inputs) in.appendTag(stack.writeToNBT(new NBTTagCompound()));
        for (ItemStack stack : outputs) out.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("Inputs", in); tag.setTag("Outputs", out); list.add(tag);
    }
}
