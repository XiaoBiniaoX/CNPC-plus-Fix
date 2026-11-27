package top.cnpcplus.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import top.cnpcplus.anvil.client.AnvilClientData;
import top.cnpcplus.anvil.network.AnvilPacketHandler;
import top.cnpcplus.anvil.network.PacketAnvilRequestList;
import top.cnpcplus.craftingview.RecipeAccess;
import top.cnpcplus.drop.client.DropClientData;
import top.cnpcplus.drop.network.DropPacketHandler;
import top.cnpcplus.drop.network.PacketDropRequestList;
import top.cnpcplus.quest.CraftStation;
import top.cnpcplus.smelting.client.SmeltingClientData;
import top.cnpcplus.smelting.network.PacketSmeltingRequestList;
import top.cnpcplus.smelting.network.SmeltingPacketHandler;
import top.cnpcplus.smithing.client.SmithingClientData;
import top.cnpcplus.smithing.network.PacketSmithingRequestList;
import top.cnpcplus.smithing.network.SmithingPacketHandler;
import top.cnpcplus.station.StationRecipes;
import top.cnpcplus.station.StationPacket;

final class QuestCraftRecipeCatalog {
    private static net.minecraft.client.multiplayer.ClientPacketListener requestedOn;

    private QuestCraftRecipeCatalog() {}

    static void requestMissingLists() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || connection == requestedOn) return;
        requestedOn = connection;
        if (AnvilClientData.get().isEmpty()) AnvilPacketHandler.CHANNEL.sendToServer(new PacketAnvilRequestList());
        if (!SmithingClientData.isLoaded()) SmithingPacketHandler.CHANNEL.sendToServer(new PacketSmithingRequestList());
        if (DropClientData.get().isEmpty()) DropPacketHandler.CHANNEL.sendToServer(new PacketDropRequestList());
        if (SmeltingClientData.get().isEmpty()) SmeltingPacketHandler.CHANNEL.sendToServer(new PacketSmeltingRequestList());
        for (int kind = 0; kind < StationRecipes.KINDS.length; kind++) {
            if (!StationRecipes.CLIENT.containsKey(kind)) {
                StationPacket.CHANNEL.sendToServer(new StationPacket(5, kind, new CompoundTag()));
            }
        }
    }

    static List<QuestCraftRecipe> recipes(String station) {
        ArrayList<QuestCraftRecipe> result = new ArrayList<>();
        switch (station) {
            case "workbench", "carpentry" -> {
                boolean global = station.equals("workbench");
                int columns = global ? 3 : 4;
                for (var recipe : RecipeAccess.getRecipes(global, true)) {
                    ItemStack output = recipe.getRecipeOutput();
                    if (output != null && !output.isEmpty()) {
                        ArrayList<ItemStack> inputs = new ArrayList<>(columns * columns);
                        for (int i = 0; i < columns * columns; i++) inputs.add(ItemStack.EMPTY);
                        for (int y = 0; y < recipe.recipeHeight; y++) {
                            for (int x = 0; x < recipe.recipeWidth; x++) {
                                int gridX = x + recipe.offsetX, gridY = y + recipe.offsetY;
                                inputs.set(gridY * columns + gridX,
                                        recipe.getCraftingItem(y * recipe.recipeWidth + x));
                            }
                        }
                        result.add(new QuestCraftRecipe(station, recipe.id.toString(),
                                recipe.name == null || recipe.name.isBlank()
                                        ? output.getHoverName().getString() : recipe.name, output, inputs, columns, columns));
                    }
                }
            }
            case "anvil" -> AnvilClientData.get().forEach(recipe -> add(result, station,
                    Integer.toString(recipe.id), recipe.name, recipe.result, recipe.inputA, recipe.inputB));
            case "smithing" -> SmithingClientData.get().forEach(recipe -> add(result, station,
                    Integer.toString(recipe.id), recipe.name, recipe.result,
                    recipe.template, recipe.base, recipe.addition));
            case "furnace", "blast_furnace", "smoker" -> SmeltingClientData.get().forEach(recipe -> {
                boolean enabled = station.equals("furnace") || station.equals("blast_furnace") && recipe.blastAllowed
                        || station.equals("smoker") && recipe.smokerAllowed;
                if (enabled && recipe.output != null && !recipe.output.isEmpty()) {
                    result.add(new QuestCraftRecipe(station, Integer.toString(recipe.id),
                            recipe.name == null || recipe.name.isBlank()
                                    ? recipe.output.getHoverName().getString() : recipe.name,
                            recipe.output, List.of(recipe.input, recipe.fuel), 1, 2,
                            List.of(recipe.output), recipe.genericFuelAllowed));
                }
            });
            case "brewing" -> {
                for (var recipe : StationRecipes.CLIENT.getOrDefault(1, List.of())) {
                    List<ItemStack> outputs = java.util.Arrays.asList(recipe.outputs);
                    ItemStack firstOutput = outputs.stream().filter(output -> !output.isEmpty())
                            .findFirst().orElse(ItemStack.EMPTY);
                    if (!firstOutput.isEmpty()) {
                        String name = recipe.name == null || recipe.name.isBlank()
                                ? firstOutput.getHoverName().getString() : recipe.name;
                        result.add(new QuestCraftRecipe(station, Integer.toString(recipe.id), name,
                                outputs, java.util.Arrays.asList(recipe.inputs), 3, 2));
                    }
                }
            }
            case "grindstone", "campfire" -> {
                int kind = station.equals("grindstone") ? 0 : 2;
                for (var recipe : StationRecipes.CLIENT.getOrDefault(kind, List.of())) {
                    ItemStack output = recipe.outputs[0];
                    if (!output.isEmpty()) add(result, station, Integer.toString(recipe.id), recipe.name,
                            output, recipe.inputs);
                }
            }
            case "drop" -> DropClientData.get().forEach(recipe -> add(result, station,
                    Integer.toString(recipe.id), recipe.name, recipe.result, recipe.inputs));
            default -> { }
        }
        result.sort(java.util.Comparator.comparing(QuestCraftRecipe::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(QuestCraftRecipe::id));
        return result;
    }

    private static void add(List<QuestCraftRecipe> result, String station, String id, String name, ItemStack output,
                            ItemStack... inputs) {
        if (output != null && !output.isEmpty()) result.add(new QuestCraftRecipe(station, id,
                name == null || name.isBlank() ? output.getHoverName().getString() : name, output,
                java.util.Arrays.asList(inputs)));
    }
}
