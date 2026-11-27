package top.cnpcplus.client.gui;

import net.minecraft.world.item.ItemStack;
import java.util.List;

record QuestCraftRecipe(String station, String id, String name, ItemStack output, List<ItemStack> inputs,
                        int columns, int rows, List<ItemStack> previewOutputs, boolean genericFuelAllowed) {
    QuestCraftRecipe(String station, String id, String name, ItemStack output, List<ItemStack> inputs,
                     int columns, int rows, List<ItemStack> previewOutputs) {
        this(station, id, name, output, inputs, columns, rows, previewOutputs, false);
    }

    QuestCraftRecipe(String station, String id, String name, ItemStack output, List<ItemStack> inputs,
                     int columns, int rows) {
        this(station, id, name, output, inputs, columns, rows, List.of(output), false);
    }

    QuestCraftRecipe(String station, String id, String name, ItemStack output, List<ItemStack> inputs) {
        this(station, id, name, output, inputs, defaultColumns(station), defaultRows(station));
    }

    QuestCraftRecipe(String station, String id, String name, List<ItemStack> outputs, List<ItemStack> inputs,
                     int columns, int rows) {
        this(station, id, name, outputs.stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY),
                inputs, columns, rows, outputs, false);
    }

    QuestCraftRecipe {
        output = output.copy();
        inputs = inputs.stream().map(ItemStack::copy).toList();
        previewOutputs = previewOutputs.stream().map(ItemStack::copy).toList();
    }

    ItemStack inputAt(int position) {
        int index = station.equals("brewing")
                ? switch (position) {
                    case 3 -> 0;
                    case 4 -> 1;
                    case 5 -> 2;
                    case 1 -> 3;
                    default -> -1;
                }
                : position;
        return index >= 0 && index < inputs.size() ? inputs.get(index) : ItemStack.EMPTY;
    }

    ItemStack previewOutputAt(int position) {
        return position >= 0 && position < previewOutputs.size() ? previewOutputs.get(position) : ItemStack.EMPTY;
    }

    private static int defaultColumns(String station) {
        return switch (station) {
            case "grindstone" -> 1;
            case "anvil" -> 2;
            case "smithing" -> 3;
            case "brewing" -> 3;
            case "drop" -> 2;
            default -> 1;
        };
    }

    private static int defaultRows(String station) {
        return station.equals("brewing") || station.equals("grindstone") || station.equals("drop") ? 2 : 1;
    }
}
