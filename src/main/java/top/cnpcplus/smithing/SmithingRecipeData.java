package top.cnpcplus.smithing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义锻造配方「源数据」。GUI 直接编辑的就是这个对象，不直接作为 Minecraft Recipe。
 * 名称可中文；id 为独立数字标识（改名不影响 id）。
 * 227：字段 = 模板/基座/升级材料/结果 4 槽（熔炼的 cookTime/xp/三开关按用户要求删除——锻造台没有时间与经验）。
 */
public class SmithingRecipeData {
    public int id = -1;
    public String name = "";
    public ItemStack template = ItemStack.EMPTY;  // 模板（如模具/纹饰）
    public ItemStack base = ItemStack.EMPTY;      // 基座（被升级物）
    public ItemStack addition = ItemStack.EMPTY;  // 升级材料
    public ItemStack result = ItemStack.EMPTY;    // 结果
    public boolean nameOnly = false;              // 230 仅名字检查（只校验物品id+物品名字；附魔/耐久/其余NBT不参与）

    /**
     * 落盘写四个槽位，显式使用当前服务端注册表。保存请求只传元数据；网络同步走物品组件 codec。
     */
    public CompoundTag toNBT(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", this.id);
        t.putString("Name", this.name == null ? "" : this.name);
        t.putBoolean("NameOnly", this.nameOnly);
        t.put("Template", this.template.saveOptional(registries));
        t.put("Base", this.base.saveOptional(registries));
        t.put("Addition", this.addition.saveOptional(registries));
        t.put("Result", this.result.saveOptional(registries));
        return t;
    }

    public static SmithingRecipeData fromNBT(HolderLookup.Provider registries, CompoundTag t) {
        SmithingRecipeData d = new SmithingRecipeData();
        if (t == null) return d;
        d.id = t.getInt("Id");
        d.name = t.getString("Name");
        d.nameOnly = t.getBoolean("NameOnly"); // 旧配方无此键 → 默认 false，兼容
        d.template = ItemStack.parseOptional(registries, t.getCompound("Template"));
        d.base = ItemStack.parseOptional(registries, t.getCompound("Base"));
        d.addition = ItemStack.parseOptional(registries, t.getCompound("Addition"));
        d.result = ItemStack.parseOptional(registries, t.getCompound("Result"));
        return d;
    }

    public SmithingRecipeData copy() {
        SmithingRecipeData d = new SmithingRecipeData();
        d.id = this.id;
        d.name = this.name;
        d.nameOnly = this.nameOnly;
        d.template = this.template.copy();
        d.base = this.base.copy();
        d.addition = this.addition.copy();
        d.result = this.result.copy();
        return d;
    }
}
