package top.cnpcplus.drop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
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
    /** 仅名字检查（只校验物品id+物品名字，附魔/耐久/其余NBT不参与）。233a GUI 开关已接。 */
    public boolean nameOnly = false;
    /** 合成音效 id（233c，"namespace:path" 字符串；空=不播放）。GUI 用 CNPC GuiSoundSelection 选择。 */
    public String soundId = "";
    /** 233b 生效维度（"minecraft:overworld" 等；空=不限维度，兼容 232 存量配方）。 */
    public String dim = "";
    /** 233b 绑定的方块坐标（dim 非空时与 hasBindPos 一起校验；false=只绑维度不绑坐标）。 */
    public boolean hasBindPos = false;
    public int bindX, bindY, bindZ;
    /** 233d 粒子特效配置（归属模型：粒子→形状子配置；空=无特效）。物理裁剪见 DropFxCaps.sanitize。 */
    public final java.util.List<top.cnpcplus.drop.fx.DropFxConfig> fxs =
            top.cnpcplus.drop.fx.DropFxCaps.newList();

    public CompoundTag toNBT(HolderLookup.Provider registries) {
        CompoundTag t = toMetadataNBT();
        for (int i = 0; i < 4; i++) t.put("Input" + i, this.inputs[i].saveOptional(registries));
        t.put("Result", this.result.saveOptional(registries));
        return t;
    }

    /**
     * 保存请求仅带元数据与粒子配置，不带物品；五槽物品始终取服务端编辑容器。
     */
    public CompoundTag toMetadataNBT() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", this.id);
        t.putString("Name", this.name == null ? "" : this.name);
        t.putBoolean("NameOnly", this.nameOnly);
        t.putString("SoundId", this.soundId == null ? "" : this.soundId);
        t.putString("BindDim", this.dim == null ? "" : this.dim);
        t.putBoolean("BindPos", this.hasBindPos);
        t.putInt("BindX", this.bindX);
        t.putInt("BindY", this.bindY);
        t.putInt("BindZ", this.bindZ);
        // 233d 粒子配置：无物品性质，任何方向（落盘/S→C/C→S）都携带
        net.minecraft.nbt.ListTag fxList = new net.minecraft.nbt.ListTag();
        for (top.cnpcplus.drop.fx.DropFxConfig c : this.fxs) fxList.add(c.toNBT());
        t.put("Fx", fxList);
        return t;
    }

    public static DropRecipeData fromMetadataNBT(CompoundTag t) {
        DropRecipeData d = new DropRecipeData();
        if (t == null) return d;
        d.id = t.getInt("Id");
        d.name = t.getString("Name");
        d.nameOnly = t.getBoolean("NameOnly");
        d.soundId = t.getString("SoundId");
        d.dim = t.getString("BindDim");
        d.hasBindPos = t.getBoolean("BindPos");
        d.bindX = t.getInt("BindX");
        d.bindY = t.getInt("BindY");
        d.bindZ = t.getInt("BindZ");
        net.minecraft.nbt.ListTag fxList = t.getList("Fx", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < fxList.size(); i++) {
            d.fxs.add(top.cnpcplus.drop.fx.DropFxConfig.fromNBT(fxList.getCompound(i)));
        }
        // 数据卫生：磁盘/网络进入一律裁剪（超限丢子项+WARN），空形状的 config 清除
        top.cnpcplus.drop.fx.DropFxCaps.sanitize(d);
        return d;
    }

    public static DropRecipeData fromNBT(HolderLookup.Provider registries, CompoundTag t) {
        DropRecipeData d = fromMetadataNBT(t);
        if (t == null) return d;
        for (int i = 0; i < 4; i++) {
            d.inputs[i] = ItemStack.parseOptional(registries, t.getCompound("Input" + i));
        }
        d.result = ItemStack.parseOptional(registries, t.getCompound("Result"));
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
        DropRecipeData d = new DropRecipeData();
        d.id = this.id;
        d.name = this.name;
        d.nameOnly = this.nameOnly;
        d.soundId = this.soundId;
        d.dim = this.dim;
        d.hasBindPos = this.hasBindPos;
        d.bindX = this.bindX;
        d.bindY = this.bindY;
        d.bindZ = this.bindZ;
        for (int i = 0; i < 4; i++) d.inputs[i] = this.inputs[i].copy();
        d.result = this.result.copy();
        for (var fx : this.fxs) d.fxs.add(fx.copy());
        return d;
    }
}
