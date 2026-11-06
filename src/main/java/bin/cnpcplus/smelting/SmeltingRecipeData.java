package bin.cnpcplus.smelting;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class SmeltingRecipeData {
    public noppes.npcs.controllers.data.Availability availability = new noppes.npcs.controllers.data.Availability();
    public int id = -1;
    public String name = "";
    public ItemStack input = ItemStack.EMPTY;
    public ItemStack fuel = ItemStack.EMPTY;
    public ItemStack output = ItemStack.EMPTY;
    public float cookTime = 200.0F;
    public float xp;
    public boolean blastAllowed;
    public boolean smokerAllowed;
    public boolean genericFuelAllowed;
    /** true = 输入按「仅名字检查」匹配（物品类型 + 显示名）；false = 原版行为（仅物品类型）。 */
    public boolean nameOnly;

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Name", name == null ? "" : name);
        tag.put("Input", input.saveOptional(registries));
        tag.put("Fuel", fuel.saveOptional(registries));
        tag.put("Output", output.saveOptional(registries));
        tag.putFloat("CookTime", Float.isFinite(cookTime) ? Math.max(0.01F, cookTime) : 200.0F);
        tag.putFloat("Xp", Float.isFinite(xp) ? xp : 0.0F);
        tag.putBoolean("Blast", blastAllowed);
        tag.putBoolean("Smoker", smokerAllowed);
        tag.putBoolean("GenericFuel", genericFuelAllowed);
        tag.putBoolean("NameOnly", nameOnly);
        tag.put("Availability", availability.save(registries, new CompoundTag()));
        return tag;
    }

    public static SmeltingRecipeData load(HolderLookup.Provider registries, CompoundTag tag) {
        SmeltingRecipeData data = new SmeltingRecipeData();
        data.id = tag.getInt("Id");
        data.name = tag.getString("Name");
        if (data.name.length() > 256) data.name = data.name.substring(0, 256);
        data.input = boundedStack(ItemStack.parseOptional(registries, tag.getCompound("Input")));
        data.fuel = boundedStack(ItemStack.parseOptional(registries, tag.getCompound("Fuel")));
        data.output = boundedStack(ItemStack.parseOptional(registries, tag.getCompound("Output")));
        data.cookTime = Float.isFinite(tag.getFloat("CookTime")) ? Math.max(0.01F, tag.getFloat("CookTime")) : 200.0F;
        data.xp = Float.isFinite(tag.getFloat("Xp")) ? tag.getFloat("Xp") : 0.0F;
        data.blastAllowed = tag.getBoolean("Blast");
        data.smokerAllowed = tag.getBoolean("Smoker");
        data.genericFuelAllowed = tag.getBoolean("GenericFuel");
        data.nameOnly = tag.getBoolean("NameOnly");
        if (tag.contains("Availability")) data.availability.load(registries, tag.getCompound("Availability"));
        return data;
    }

    public SmeltingRecipeData copy(HolderLookup.Provider registries) {
        return load(registries, save(registries));
    }

    private static ItemStack boundedStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        stack.setCount(Math.min(stack.getCount(), 64));
        return stack;
    }
}
