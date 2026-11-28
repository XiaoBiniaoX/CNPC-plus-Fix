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
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
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
        if (station == CraftStation.WORKBENCH) {
            for (IRecipe recipe : ForgeRegistries.RECIPES) {
                if (recipe.getRegistryName() == null || recipe.getRecipeOutput().isEmpty()) continue;
                List<ItemStack> inputs = new ArrayList<>();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    ItemStack[] alternatives = ingredient.getMatchingStacks();
                    inputs.add(alternatives.length == 0 ? ItemStack.EMPTY : alternatives[0]);
                }
                add(result, recipe.getRegistryName().toString(), recipe instanceof noppes.npcs.controllers.data.RecipeCarpentry
                        ? ((noppes.npcs.controllers.data.RecipeCarpentry)recipe).name : "", 3,
                        Arrays.asList(recipe.getRecipeOutput()), inputs);
            }
        } else if (station == CraftStation.CARPENTRY) {
            for (RecipeView recipe : RecipeAccess.getAnvilRecipes()) {
                List<ItemStack> grid = new ArrayList<>(java.util.Collections.nCopies(16, ItemStack.EMPTY));
                for (int y = 0; y < recipe.recipeHeight; y++) for (int x = 0; x < recipe.recipeWidth; x++) {
                    int at = (y + recipe.offsetY) * 4 + x + recipe.offsetX;
                    if (at >= 0 && at < 16) grid.set(at, recipe.getIngredient(y * recipe.recipeWidth + x));
                }
                add(result, Integer.toString(recipe.id), recipe.name, 4, Arrays.asList(recipe.output), grid);
            }
        } else if (station == CraftStation.SMITHING || station == CraftStation.ANVIL || station == CraftStation.DROP) {
            CraftKind kind = station == CraftStation.SMITHING ? CraftKind.SMITHING : station == CraftStation.ANVIL ? CraftKind.ANVIL : CraftKind.DROP;
            for (CustomRecipe recipe : RecipeStore.list(kind))
                add(result, Integer.toString(recipe.id), recipe.name, kind == CraftKind.DROP ? 3 : recipe.inputs.length,
                        Arrays.asList(recipe.result), Arrays.asList(recipe.inputs));
        } else if (station == CraftStation.FURNACE) {
            for (SmeltingRecipeData recipe : SmeltingRecipeRegistry.list())
                add(result, Integer.toString(recipe.id), recipe.name, 1, Arrays.asList(recipe.output), Arrays.asList(recipe.input, recipe.fuel));
        } else {
            int kind = station == CraftStation.GRINDSTONE ? 0 : station == CraftStation.BREWING ? 1 : 2;
            for (StationRecipe recipe : StationRecipes.catalogue(kind).values())
                add(result, Integer.toString(recipe.id), recipe.name, kind == 1 ? 3 : kind == 0 ? 2 : 1,
                        Arrays.asList(recipe.outputs), Arrays.asList(recipe.inputs));
        }
        if (station == CraftStation.FURNACE || station == CraftStation.CAMPFIRE) {
            for (Map.Entry<ItemStack, ItemStack> recipe : FurnaceRecipes.instance().getSmeltingList().entrySet())
                add(result, "vanilla", "", 1, Arrays.asList(recipe.getValue()), Arrays.asList(recipe.getKey()));
        } else if (station == CraftStation.BREWING) {
            for (net.minecraft.potion.PotionType potion : net.minecraft.potion.PotionType.REGISTRY)
                for (net.minecraft.item.Item bottle : new net.minecraft.item.Item[]{net.minecraft.init.Items.POTIONITEM,
                        net.minecraft.init.Items.SPLASH_POTION, net.minecraft.init.Items.LINGERING_POTION})
                    add(result, "vanilla", "", 1, Arrays.asList(net.minecraft.potion.PotionUtils.addPotionToItemStack(new ItemStack(bottle), potion)),
                            java.util.Collections.emptyList());
        } else if (station == CraftStation.ANVIL || station == CraftStation.GRINDSTONE) {
            add(result, "vanilla", "cnpcplus.quest.recipe.vanilla", 1, java.util.Collections.emptyList(), java.util.Collections.emptyList());
        }
        return result;
    }

    private static void add(List<NBTTagCompound> list, String id, String name, int columns,
                            List<ItemStack> outputs, List<ItemStack> inputs) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Id", id); tag.setString("Name", name == null ? "" : name); tag.setInteger("Columns", columns);
        NBTTagList in = new NBTTagList(), out = new NBTTagList();
        for (ItemStack stack : inputs) in.appendTag(stack.writeToNBT(new NBTTagCompound()));
        for (ItemStack stack : outputs) if (!stack.isEmpty()) out.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("Inputs", in); tag.setTag("Outputs", out); list.add(tag);
    }
}
