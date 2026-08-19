package bin.cnpcplus.smelting;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class SmeltingRecipeData {
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

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Name", name == null ? "" : name);
        tag.put("Input", input.save(registries, new CompoundTag()));
        tag.put("Fuel", fuel.save(registries, new CompoundTag()));
        tag.put("Output", output.save(registries, new CompoundTag()));
        tag.putFloat("CookTime", Float.isFinite(cookTime) ? Math.max(0.01F, cookTime) : 200.0F);
        tag.putFloat("Xp", Float.isFinite(xp) ? xp : 0.0F);
        tag.putBoolean("Blast", blastAllowed);
        tag.putBoolean("Smoker", smokerAllowed);
        tag.putBoolean("GenericFuel", genericFuelAllowed);
        return tag;
    }

    public static SmeltingRecipeData load(HolderLookup.Provider registries, CompoundTag tag) {
        SmeltingRecipeData data = new SmeltingRecipeData();
        data.id = tag.getInt("Id");
        data.name = tag.getString("Name");
        if (data.name.length() > 256) data.name = data.name.substring(0, 256);
        data.input = boundedStack(ItemStack.parse(registries, tag.getCompound("Input")).orElse(ItemStack.EMPTY));
        data.fuel = boundedStack(ItemStack.parse(registries, tag.getCompound("Fuel")).orElse(ItemStack.EMPTY));
        data.output = boundedStack(ItemStack.parse(registries, tag.getCompound("Output")).orElse(ItemStack.EMPTY));
        data.cookTime = Float.isFinite(tag.getFloat("CookTime")) ? Math.max(0.01F, tag.getFloat("CookTime")) : 200.0F;
        data.xp = Float.isFinite(tag.getFloat("Xp")) ? tag.getFloat("Xp") : 0.0F;
        data.blastAllowed = tag.getBoolean("Blast");
        data.smokerAllowed = tag.getBoolean("Smoker");
        data.genericFuelAllowed = tag.getBoolean("GenericFuel");
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
