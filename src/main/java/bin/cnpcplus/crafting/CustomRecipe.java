package bin.cnpcplus.crafting;

import bin.cnpcplus.fx.FxConfig;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.ArrayList;
import java.util.List;

/** 三类配方共享编辑/存储流程，NBT 字段保持各自的 1.20.1 格式。 */
public final class CustomRecipe {
    public final CraftKind kind;
    public int id = -1;
    public String name = "", sound = "", dimension = "";
    public boolean nameOnly, bindPosition;
    public int x, y, z, experience = 1;
    public final ItemStack[] inputs;
    public ItemStack result = ItemStack.EMPTY;
    public List<FxConfig> effects = new ArrayList<>();

    public CustomRecipe(CraftKind kind) {
        this.kind = kind;
        inputs = new ItemStack[kind.inputKeys.length];
        java.util.Arrays.fill(inputs, ItemStack.EMPTY);
    }

    public NBTTagCompound toNBT(boolean items) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Id", id);
        tag.setString("Name", name);
        tag.setBoolean("NameOnly", nameOnly);
        if (kind == CraftKind.ANVIL) tag.setInteger("ExpCost", experience);
        if (kind == CraftKind.DROP) {
            tag.setString("SoundId", sound);
            tag.setString("BindDim", dimension);
            tag.setBoolean("BindPos", bindPosition);
            tag.setInteger("BindX", x); tag.setInteger("BindY", y); tag.setInteger("BindZ", z);
            tag.setTag("Fx", FxConfig.write(effects));
        }
        if (items) {
            for (int i = 0; i < inputs.length; i++) tag.setTag(kind.inputKeys[i], inputs[i].writeToNBT(new NBTTagCompound()));
            tag.setTag("Result", result.writeToNBT(new NBTTagCompound()));
        }
        return tag;
    }

    public static CustomRecipe fromNBT(CraftKind kind, NBTTagCompound tag) {
        CustomRecipe recipe = new CustomRecipe(kind);
        recipe.id = tag.hasKey("Id", 99) ? tag.getInteger("Id") : -1;
        recipe.name = SmeltingRecipeData.clampName(tag.getString("Name"));
        recipe.nameOnly = tag.getBoolean("NameOnly");
        recipe.experience = tag.hasKey("ExpCost", 99) ? Math.max(-32767, Math.min(32767, tag.getInteger("ExpCost"))) : 1;
        recipe.sound = SmeltingRecipeData.clampName(tag.getString("SoundId"));
        recipe.dimension = SmeltingRecipeData.clampName(tag.getString("BindDim"));
        recipe.bindPosition = tag.getBoolean("BindPos");
        recipe.x = tag.getInteger("BindX"); recipe.y = tag.getInteger("BindY"); recipe.z = tag.getInteger("BindZ");
        recipe.effects = FxConfig.read(tag.getTagList("Fx", 10));
        for (int i = 0; i < recipe.inputs.length; i++) recipe.inputs[i] = stack(tag.getCompoundTag(kind.inputKeys[i]));
        recipe.result = stack(tag.getCompoundTag("Result"));
        return recipe;
    }

    private static ItemStack stack(NBTTagCompound tag) {
        ItemStack stack = new ItemStack(tag);
        if (!stack.isEmpty()) stack.setCount(Math.min(64, stack.getCount()));
        return stack;
    }

    /**
     * 各类型的最少输入不同：锻造只要模板+基座（附加格可空），铁砧两格都必须有，
     * 掉落物至少两样。原来三类共用 "count == inputs.length" 会让锻造和铁砧永远存不进去。
     */
    public boolean valid() {
        if (result.isEmpty()) return false;
        if (kind == CraftKind.SMITHING) return !inputs[0].isEmpty() && !inputs[1].isEmpty();
        int count = 0;
        for (ItemStack input : inputs) if (!input.isEmpty()) count++;
        return kind == CraftKind.ANVIL ? count == inputs.length : count >= 2;
    }

    public CustomRecipe copy() { return fromNBT(kind, toNBT(true)); }

    public static boolean matches(ItemStack have, ItemStack need, boolean nameOnly) {
        if (have.isEmpty() || need.isEmpty()) return have.isEmpty() && need.isEmpty();
        if (have.getItem() != need.getItem()) return false;
        return nameOnly ? have.getDisplayName().equals(need.getDisplayName())
                : have.getMetadata() == need.getMetadata() && ItemStack.areItemStackTagsEqual(have, need);
    }
}
