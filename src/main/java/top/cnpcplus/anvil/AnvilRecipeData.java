package top.cnpcplus.anvil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义铁砧配方「源数据」（阶段231 从 SmithingRecipeData 复制改三槽+经验）。
 * GUI 直接编辑的就是这个对象；不注册进 Minecraft RecipeManager——
 * 铁砧 vanilla AnvilMenu 不查 RecipeManager（反编译实证），配方由 AnvilCraftHandler
 * 在 Forge AnvilUpdateEvent 里直查本表生效。
 * 字段：A 槽 + B 槽 + 结果（A+B 敲打=C）+ 敲打一次消耗经验 + 仅名字检查开关。
 */
public class AnvilRecipeData {
    public int id = -1;
    public String name = "";
    public ItemStack inputA = ItemStack.EMPTY;   // A 槽（消耗 1 个，onTake 原生无条件清空）
    public ItemStack inputB = ItemStack.EMPTY;   // B 槽（消耗 1 个，setMaterialCost(1) shrink）
    public ItemStack result = ItemStack.EMPTY;   // 结果槽
    /**
     * 敲打一次消耗的经验（等级），231b 起允许 -32767..32767：正数=扣锻造者、负数=给锻造者
     * （onTake 原生 giveExperienceLevels(-cost) 实证）、0=免费；可取走由 MixinAnvilMenu 放宽
     * vanilla mayPickup 的 cost>0 条件保证。
     */
    public int expCost = 1;
    /** 仅名字检查（只校验物品id+物品名字，附魔/耐久/其余NBT不参与，=CNPC NoppesUtilPlayer 同链）。 */
    public boolean nameOnly = false;

    public CompoundTag toNBT() {
        return this.toNBT(true);
    }

    /**
     * @param withItems 是否写入三个槽位物品。
     *   客户端往服务端发保存请求时传 false：服务端只认自己容器里的 ItemStack（见 PacketAnvilSave），
     *   带上去纯属废数据，还会把 C→S 载荷推向 32767 字节硬上限（超限玩家直接被踢）。
     *   落盘与 S→C 同步必须传 true。
     */
    public CompoundTag toNBT(boolean withItems) {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", this.id);
        t.putString("Name", this.name == null ? "" : this.name);
        t.putInt("ExpCost", this.expCost);
        t.putBoolean("NameOnly", this.nameOnly);
        if (withItems) {
            t.put("InputA", this.inputA.save(new CompoundTag()));
            t.put("InputB", this.inputB.save(new CompoundTag()));
            t.put("Result", this.result.save(new CompoundTag()));
        }
        return t;
    }

    public static AnvilRecipeData fromNBT(CompoundTag t) {
        AnvilRecipeData d = new AnvilRecipeData();
        d.id = t.getInt("Id");
        d.name = t.getString("Name");
        // 旧档/异常档兼容：经验缺键=1，越界夹回 -32767..32767（231b 放宽，负数=给锻造者经验）
        d.expCost = t.contains("ExpCost") ? t.getInt("ExpCost") : 1;
        if (d.expCost < -32767) d.expCost = -32767;
        if (d.expCost > 32767) d.expCost = 32767;
        d.nameOnly = t.getBoolean("NameOnly");
        d.inputA = ItemStack.of(t.getCompound("InputA"));
        d.inputB = ItemStack.of(t.getCompound("InputB"));
        d.result = ItemStack.of(t.getCompound("Result"));
        return d;
    }

    public AnvilRecipeData copy() {
        return fromNBT(this.toNBT());
    }
}
