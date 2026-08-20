package bin.cnpcplus.smelting;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * One visual smelting recipe as edited in the GUI.
 *
 * Field names and NBT keys are kept identical to the 1.20.1 / 1.21.1 modules so
 * a smelting_recipes.dat file stays readable across game versions.
 *
 * blastAllowed / smokerAllowed have no matching device in 1.12.2 (there is only
 * TileEntityFurnace). They are still stored and shown so the data format stays
 * compatible; the GUI tooltip says they do nothing here.
 */
public class SmeltingRecipeData {
    /** Same clamp the higher versions use, keeps hostile NBT out of the furnace. */
    private static final int MAX_NAME_LENGTH = 256;
    private static final float MIN_COOK_TIME = 0.01F;
    private static final float MAX_COOK_TIME = 100000.0F;
    private static final float MAX_ABS_XP = 100000.0F;

    public int id = -1;
    public String name = "";
    public ItemStack input = ItemStack.EMPTY;
    public ItemStack fuel = ItemStack.EMPTY;
    public ItemStack output = ItemStack.EMPTY;
    public float cookTime = 200.0F;
    public float xp = 0.0F;
    public boolean blastAllowed = false;
    public boolean smokerAllowed = false;
    public boolean genericFuelAllowed = false;

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Id", this.id);
        tag.setString("Name", this.name == null ? "" : this.name);
        tag.setTag("Input", this.input.writeToNBT(new NBTTagCompound()));
        tag.setTag("Fuel", this.fuel.writeToNBT(new NBTTagCompound()));
        tag.setTag("Output", this.output.writeToNBT(new NBTTagCompound()));
        tag.setFloat("CookTime", this.cookTime);
        tag.setFloat("Xp", this.xp);
        tag.setBoolean("Blast", this.blastAllowed);
        tag.setBoolean("Smoker", this.smokerAllowed);
        tag.setBoolean("GenericFuel", this.genericFuelAllowed);
        return tag;
    }

    public static SmeltingRecipeData fromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }
        SmeltingRecipeData data = new SmeltingRecipeData();
        data.id = tag.getInteger("Id");
        data.name = clampName(tag.getString("Name"));
        data.input = readStack(tag, "Input");
        data.fuel = readStack(tag, "Fuel");
        data.output = readStack(tag, "Output");
        data.cookTime = clampCookTime(tag.getFloat("CookTime"));
        data.xp = clampXp(tag.getFloat("Xp"));
        data.blastAllowed = tag.getBoolean("Blast");
        data.smokerAllowed = tag.getBoolean("Smoker");
        data.genericFuelAllowed = tag.getBoolean("GenericFuel");
        return data;
    }

    /** Deep copy through NBT, so callers can never hold a reference into the registry cache. */
    public SmeltingRecipeData copy() {
        return fromNBT(this.toNBT());
    }

    private static ItemStack readStack(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(tag.getCompoundTag(key));
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // A count above the stack limit would let a crafted packet duplicate items.
        if (stack.getCount() > 64) {
            stack.setCount(64);
        }
        return stack;
    }

    public static String clampName(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_NAME_LENGTH ? value.substring(0, MAX_NAME_LENGTH) : value;
    }

    /** Rejects NaN and Infinity: those would poison the furnace progress maths. */
    public static float clampCookTime(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 200.0F;
        }
        return Math.max(MIN_COOK_TIME, Math.min(MAX_COOK_TIME, value));
    }

    public static float clampXp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0F;
        }
        return Math.max(-MAX_ABS_XP, Math.min(MAX_ABS_XP, value));
    }
}
