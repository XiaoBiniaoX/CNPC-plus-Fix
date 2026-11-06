package top.cnpcplus.station;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.controllers.data.Availability;

/** Shared editor model; each station has an independent persisted catalogue. */
public final class StationRecipe {
    public int id = -1;
    public String name = "new";
    public boolean nameOnly;
    public int time = 200, experience;
    public Availability availability = new Availability();
    public final ItemStack[] inputs = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    public final ItemStack[] outputs = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    public CompoundTag saveMetadata(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Name", name);
        tag.putBoolean("NameOnly", nameOnly);
        tag.putInt("Time", time);
        tag.putInt("Experience", experience);
        tag.put("Availability", availability.save(registries, new CompoundTag()));
        return tag;
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = saveMetadata(registries);
        for (int i = 0; i < inputs.length; i++) tag.put("Input" + i, inputs[i].saveOptional(registries));
        for (int i = 0; i < outputs.length; i++) tag.put("Output" + i, outputs[i].saveOptional(registries));
        return tag;
    }
    public static StationRecipe readMetadata(CompoundTag tag, HolderLookup.Provider registries) {
        StationRecipe recipe = new StationRecipe();
        recipe.id = tag.getInt("Id");
        recipe.name = tag.getString("Name");
        recipe.nameOnly = tag.getBoolean("NameOnly");
        recipe.time = Math.max(1, Math.min(100000, tag.getInt("Time")));
        recipe.experience = Math.max(-32767, Math.min(32767, tag.getInt("Experience")));
        if (tag.contains("Availability")) recipe.availability.load(registries, tag.getCompound("Availability"));
        return recipe;
    }
    public static StationRecipe read(CompoundTag tag, HolderLookup.Provider registries) {
        StationRecipe recipe = readMetadata(tag, registries);
        for (int i = 0; i < recipe.inputs.length; i++) recipe.inputs[i] = ItemStack.parseOptional(registries, tag.getCompound("Input" + i));
        for (int i = 0; i < recipe.outputs.length; i++) recipe.outputs[i] = ItemStack.parseOptional(registries, tag.getCompound("Output" + i));
        return recipe;
    }
    public StationRecipe copy() {
        StationRecipe copy = new StationRecipe();
        copy.id = id; copy.name = name; copy.nameOnly = nameOnly; copy.time = time; copy.experience = experience;
        copy.availability = top.cnpcplus.craftingview.RecipeAvailability.copy(availability);
        for (int i = 0; i < inputs.length; i++) copy.inputs[i] = inputs[i].copy();
        for (int i = 0; i < outputs.length; i++) copy.outputs[i] = outputs[i].copy();
        return copy;
    }
    public static boolean matches(ItemStack actual, ItemStack template, boolean nameOnly) {
        if (template.isEmpty()) return actual.isEmpty();
        return !actual.isEmpty() && actual.getItem() == template.getItem() && actual.getCount() >= template.getCount()
                && (nameOnly ? actual.getHoverName().getString().equals(template.getHoverName().getString()) : ItemStack.isSameItemSameComponents(actual, template));
    }
    public boolean matchesInputs(ItemStack[] actual) {
        for (int i = 0; i < actual.length; i++) if (!matches(actual[i], inputs[i], nameOnly)) return false;
        return true;
    }
}
