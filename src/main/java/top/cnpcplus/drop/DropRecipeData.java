package top.cnpcplus.drop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义掉落物配方「源数据」（阶段232 从 AnvilRecipeData 复制改四输入+结果）。
 * GUI 直接编辑的就是这个对象；不注册进 Minecraft RecipeManager——
 * 配方由 DropCraftHandler 在玩家丢出物品落地时直查本表生效（服务端权威）。
 * 字段：2×2 四个输入槽 + 结果 + 仅名字检查开关（232 本轮 GUI 不放开关，data 先留字段=后续接口）。
 * 槽内 ItemStack 的 count 即该槽的消耗数量（如 木板×3）；空槽=该槽无需求。
 */
public class DropRecipeData {
    public int id = -1;
    public String name = "";
    /** 2×2 四个输入槽（槽内 count=消耗数量；空=无需求；至少两槽有物才能注册，见 PacketDropSave）。 */
    public final ItemStack[] inputs = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    /** 结果槽（count=产出数量）。 */
    public ItemStack result = ItemStack.EMPTY;
    /** 仅名字检查（只校验物品id+物品名字，附魔/耐久/其余NBT不参与）。232 本轮 GUI 不放开关，先留字段。 */
    public boolean nameOnly = false;

    public CompoundTag toNBT() {
        return this.toNBT(true);
    }

    /**
     * @param withItems 是否写入五个槽位物品。
     *   客户端往服务端发保存请求时传 false：服务端只认自己容器里的 ItemStack（见 PacketDropSave），
     *   带上去纯属废数据，还会把 C→S 载荷推向 32767 字节硬上限（超限玩家直接被踢）。
     *   落盘与 S→C 同步必须传 true。
     */
    public CompoundTag toNBT(boolean withItems) {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", this.id);
        t.putString("Name", this.name == null ? "" : this.name);
        t.putBoolean("NameOnly", this.nameOnly);
        if (withItems) {
            for (int i = 0; i < 4; i++) {
                t.put("Input" + i, this.inputs[i].save(new CompoundTag()));
            }
            t.put("Result", this.result.save(new CompoundTag()));
        }
        return t;
    }

    public static DropRecipeData fromNBT(CompoundTag t) {
        DropRecipeData d = new DropRecipeData();
        d.id = t.getInt("Id");
        d.name = t.getString("Name");
        d.nameOnly = t.getBoolean("NameOnly");
        for (int i = 0; i < 4; i++) {
            d.inputs[i] = ItemStack.of(t.getCompound("Input" + i));
        }
        d.result = ItemStack.of(t.getCompound("Result"));
        return d;
    }

    /** 非空输入槽数量（注册条件=至少 2）。 */
    public int filledInputCount() {
        int n = 0;
        for (ItemStack s : this.inputs) {
            if (!s.isEmpty()) n++;
        }
        return n;
    }

    public DropRecipeData copy() {
        return fromNBT(this.toNBT());
    }
}
