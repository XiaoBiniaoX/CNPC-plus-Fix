package bin.cnpcplus.station;

import bin.cnpcplus.crafting.CustomRecipe;
import bin.cnpcplus.craftingview.RecipeAvailability;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.Availability;

public final class StationRecipe {
    public int id = -1, time = 200, experience;
    public String name = "new";
    public boolean nameOnly, shapeless;
    public Availability availability = new Availability();
    public final ItemStack[] inputs = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    public final ItemStack[] outputs = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    public NBTTagCompound save(boolean items) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Id", id); tag.setString("Name", name); tag.setBoolean("NameOnly", nameOnly);
        tag.setBoolean("Shapeless", shapeless);
        tag.setInteger("Time", time); tag.setInteger("Experience", experience);
        tag.setTag("Availability", availability.writeToNBT(new NBTTagCompound()));
        if (items) {
            for (int i = 0; i < 4; i++) tag.setTag("Input" + i, inputs[i].writeToNBT(new NBTTagCompound()));
            for (int i = 0; i < 3; i++) tag.setTag("Output" + i, outputs[i].writeToNBT(new NBTTagCompound()));
        }
        return tag;
    }
    public static StationRecipe read(NBTTagCompound tag) {
        StationRecipe r = new StationRecipe();
        r.id = tag.getInteger("Id"); r.name = bin.cnpcplus.smelting.SmeltingRecipeData.clampName(tag.getString("Name"));
        r.nameOnly = tag.getBoolean("NameOnly");
        r.shapeless = tag.getBoolean("Shapeless");
        r.time = tag.hasKey("Time", 99) ? Math.max(1, Math.min(100000, tag.getInteger("Time"))) : 200;
        r.experience = Math.max(-32767, Math.min(32767, tag.getInteger("Experience")));
        r.availability = RecipeAvailability.read(tag.getCompoundTag("Availability"));
        for (int i = 0; i < 4; i++) r.inputs[i] = new ItemStack(tag.getCompoundTag("Input" + i));
        for (int i = 0; i < 3; i++) r.outputs[i] = new ItemStack(tag.getCompoundTag("Output" + i));
        return r;
    }
    public boolean matchesInputs(ItemStack[] actual) {
        return matchSlots(actual) != null;
    }
    public int[] matchSlots(ItemStack[] actual) {
        if (actual.length == 4) {
            if (!CustomRecipe.matches(actual[3], inputs[3], nameOnly) || actual[3].getCount() < inputs[3].getCount()) return null;
            return CustomRecipe.matchSlots(java.util.Arrays.copyOf(actual, 3), inputs, nameOnly, shapeless, true);
        }
        return CustomRecipe.matchSlots(actual, inputs, nameOnly, shapeless, false);
    }
    public boolean valid(int kind) {
        boolean input = false, output = false;
        for (int i = 0; i < (kind == 0 ? 2 : kind == 1 ? 4 : 1); i++) input |= !inputs[i].isEmpty();
        for (int i = 0; i < (kind == 1 ? 3 : 1); i++) output |= !outputs[i].isEmpty();
        return input && output;
    }
}
