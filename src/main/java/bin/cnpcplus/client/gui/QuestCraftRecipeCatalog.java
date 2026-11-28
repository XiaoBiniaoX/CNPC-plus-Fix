package bin.cnpcplus.client.gui;

import bin.cnpcplus.quest.CraftStation;
import bin.cnpcplus.quest.QuestRecipeCatalogPacket;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.translation.I18n;

public final class QuestCraftRecipeCatalog {
    private static final Map<CraftStation, List<Recipe>> RECIPES = new EnumMap<>(CraftStation.class);
    static int revision;
    private QuestCraftRecipeCatalog() {}
    static void request() {
        RECIPES.clear(); revision++;
        for (CraftStation station : CraftStation.values())
            CraftingViewNetwork.CHANNEL.sendToServer(new QuestRecipeCatalogPacket(station));
    }
    public static void receive(CraftStation station, boolean append, NBTTagList tags) {
        List<Recipe> recipes = append ? RECIPES.computeIfAbsent(station, key -> new ArrayList<>()) : new ArrayList<>();
        for (int i = 0; i < tags.tagCount(); i++) recipes.add(new Recipe(tags.getCompoundTagAt(i)));
        recipes.sort(Comparator.comparing((Recipe r) -> r.name, String.CASE_INSENSITIVE_ORDER).thenComparing(r -> r.id));
        RECIPES.put(station, recipes); revision++;
    }
    static List<Recipe> recipes(CraftStation station) { return RECIPES.getOrDefault(station, Collections.emptyList()); }
    static final class Recipe {
        final String id, name;
        final int columns;
        final boolean genericFuelAllowed;
        final List<ItemStack> inputs = new ArrayList<>(), outputs = new ArrayList<>(), previewOutputs = new ArrayList<>();
        Recipe(NBTTagCompound tag) {
            id = tag.getString("Id"); columns = tag.getInteger("Columns");
            genericFuelAllowed = tag.getBoolean("GenericFuel");
            NBTTagList in = tag.getTagList("Inputs", 10), out = tag.getTagList("Outputs", 10);
            for (int i = 0; i < in.tagCount(); i++) inputs.add(new ItemStack(in.getCompoundTagAt(i)));
            for (int i = 0; i < out.tagCount(); i++) {
                ItemStack stack = new ItemStack(out.getCompoundTagAt(i));
                previewOutputs.add(stack);
                if (!stack.isEmpty()) outputs.add(stack);
            }
            String label = tag.getString("Name");
            name = label.isEmpty() ? (outputs.isEmpty() ? id : outputs.get(0).getDisplayName()) : I18n.translateToLocal(label);
        }
    }
}
