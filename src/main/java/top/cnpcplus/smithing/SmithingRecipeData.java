package top.cnpcplus.smithing;

import net.minecraft.nbt.CompoundTag;
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

    public CompoundTag toNBT() {
        return this.toNBT(true);
    }

    /**
     * @param withItems 是否写入四个槽位物品。
     *   客户端往服务端发保存请求时传 false：服务端只认自己容器里的 ItemStack（见 PacketSmithingSave），
     *   带上去纯属废数据，还会把 C→S 载荷推向 32767 字节硬上限（超限玩家直接被踢）。
     *   落盘与 S→C 同步必须传 true。
     */
    public CompoundTag toNBT(boolean withItems) {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", this.id);
        t.putString("Name", this.name == null ? "" : this.name);
        t.putBoolean("NameOnly", this.nameOnly);
        if (withItems) {
            t.put("Template", this.template.save(new CompoundTag()));
            t.put("Base", this.base.save(new CompoundTag()));
            t.put("Addition", this.addition.save(new CompoundTag()));
            t.put("Result", this.result.save(new CompoundTag()));
        }
        return t;
    }

    public static SmithingRecipeData fromNBT(CompoundTag t) {
        SmithingRecipeData d = new SmithingRecipeData();
        d.id = t.getInt("Id");
        d.name = t.getString("Name");
        d.nameOnly = t.getBoolean("NameOnly"); // 旧配方无此键 → 默认 false，兼容
        d.template = ItemStack.of(t.getCompound("Template"));
        d.base = ItemStack.of(t.getCompound("Base"));
        d.addition = ItemStack.of(t.getCompound("Addition"));
        d.result = ItemStack.of(t.getCompound("Result"));
        return d;
    }

    public SmithingRecipeData copy() {
        return fromNBT(this.toNBT());
    }
}
