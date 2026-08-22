package top.cnpcplus.smelting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义熔炼配方「源数据」。GUI 直接编辑的就是这个对象，不直接作为 Minecraft Recipe。
 * 名称可中文；id 为独立数字标识（改名不影响 id）。
 */
public class SmeltingRecipeData {
    public int id = -1;
    public String name = "";
    public ItemStack input = ItemStack.EMPTY;   // 被熔炼物
    public ItemStack fuel = ItemStack.EMPTY;    // 自定义燃料（可为空）
    public ItemStack output = ItemStack.EMPTY;  // 熔炼物（可堆叠）
    public float cookTime = 200.0f;             // 熔炼时间(刻)，≥0.01
    public float xp = 0.0f;                     // 熔炼经验，可负
    public boolean blastAllowed = false;        // 高炉允许
    public boolean smokerAllowed = false;       // 烟熏炉允许
    public boolean genericFuelAllowed = false;  // 通用燃料允许

    public CompoundTag toNBT() {
        return this.toNBT(true);
    }

    /**
     * @param withItems 是否写入三个槽位物品。
     *   客户端往服务端发保存请求时传 false：服务端只认自己容器里的 ItemStack（见 PacketSmeltingSave），
     *   带上去纯属废数据，还会把 C→S 载荷推向 32767 字节硬上限（超限玩家直接被踢）。
     *   落盘与 S→C 同步必须传 true。
     */
    public CompoundTag toNBT(boolean withItems) {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", this.id);
        t.putString("Name", this.name == null ? "" : this.name);
        if (withItems) {
            t.put("Input", this.input.save(new CompoundTag()));
            t.put("Fuel", this.fuel.save(new CompoundTag()));
            t.put("Output", this.output.save(new CompoundTag()));
        }
        t.putFloat("CookTime", this.cookTime);
        t.putFloat("Xp", this.xp);
        t.putBoolean("Blast", this.blastAllowed);
        t.putBoolean("Smoker", this.smokerAllowed);
        t.putBoolean("GenericFuel", this.genericFuelAllowed);
        return t;
    }

    public static SmeltingRecipeData fromNBT(CompoundTag t) {
        SmeltingRecipeData d = new SmeltingRecipeData();
        d.id = t.getInt("Id");
        d.name = t.getString("Name");
        d.input = ItemStack.of(t.getCompound("Input"));
        d.fuel = ItemStack.of(t.getCompound("Fuel"));
        d.output = ItemStack.of(t.getCompound("Output"));
        d.cookTime = Math.max(0.01f, t.getFloat("CookTime"));
        d.xp = t.getFloat("Xp");
        d.blastAllowed = t.getBoolean("Blast");
        d.smokerAllowed = t.getBoolean("Smoker");
        d.genericFuelAllowed = t.getBoolean("GenericFuel");
        return d;
    }

    public SmeltingRecipeData copy() {
        return fromNBT(this.toNBT());
    }
}
