package top.cnpcplus.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.controllers.data.Availability;

/** One shared editor model, with separate persisted catalogues for the three stations. */
public final class StationRecipe {
    public int id = -1;
    public String name = "new";
    public boolean nameOnly;
    public int time = 200, experience;
    public Availability availability = new Availability();
    public final ItemStack[] inputs = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    public final ItemStack[] outputs = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    public CompoundTag save(boolean items) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Name", name);
        tag.putBoolean("NameOnly", nameOnly);
        tag.putInt("Time", time);
        tag.putInt("Experience", experience);
        tag.put("Availability", availability.save(new CompoundTag()));
        if (items) {
            for (int i = 0; i < inputs.length; i++) tag.put("Input" + i, inputs[i].save(new CompoundTag()));
            for (int i = 0; i < outputs.length; i++) tag.put("Output" + i, outputs[i].save(new CompoundTag()));
        }
        return tag;
    }

    public static StationRecipe read(CompoundTag tag) {
        StationRecipe recipe = new StationRecipe();
        recipe.id = tag.getInt("Id");
        recipe.name = tag.getString("Name");
        recipe.nameOnly = tag.getBoolean("NameOnly");
        recipe.time = Math.max(1, Math.min(100000, tag.getInt("Time")));
        recipe.experience = Math.max(-32767, Math.min(32767, tag.getInt("Experience")));
        recipe.availability.load(tag.getCompound("Availability"));
        for (int i = 0; i < recipe.inputs.length; i++) recipe.inputs[i] = ItemStack.of(tag.getCompound("Input" + i));
        for (int i = 0; i < recipe.outputs.length; i++) recipe.outputs[i] = ItemStack.of(tag.getCompound("Output" + i));
        return recipe;
    }

    public static boolean matches(ItemStack actual, ItemStack template, boolean nameOnly) {
        if (template.isEmpty()) return actual.isEmpty();
        return !actual.isEmpty() && actual.getItem() == template.getItem() && actual.getCount() >= template.getCount()
                && (nameOnly ? actual.getHoverName().getString().equals(template.getHoverName().getString()) : ItemStack.isSameItemSameTags(actual, template));
    }

    public boolean matchesInputs(ItemStack[] actual) {
        for (int i = 0; i < actual.length; i++) if (!matches(actual[i], inputs[i], nameOnly)) return false;
        return true;
    }
}
