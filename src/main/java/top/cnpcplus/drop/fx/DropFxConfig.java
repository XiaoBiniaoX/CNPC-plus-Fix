package top.cnpcplus.drop.fx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 粒子配置归属对象（233d 数据模型中枢）：一个粒子注册 id + 它名下的多个形状子配置。
 * 归属关系（用户原话「粒子类型是配置归属对象，形状预设是他下面的独立子配置」）：
 * <pre>
 * DropRecipeData.fxs ──List──▶ DropFxConfig（particleId="minecraft:flame"）
 *                                  └─List─▶ ShapePreset（形状1）/ ShapePreset（形状2）…
 * </pre>
 * 同一粒子在同一配方中只会有一个 DropFxConfig（GUI 选中时聚合子形状）；
 * 不同粒子互不影响；不同配方各自独立持有整棵树。
 *
 * <p>particleId 用注册表全名字符串（含 mods 的粒子，如 "alexsmobs:aurora"）——
 * 存字符串而非数字 id：数字 id 跨世界/跨包不稳定（1.20.1 动态 id）。
 * 运行时解析见 {@code DropFxEngine}：ForgeRegistries.PARTICLE_TYPES.getValue。
 */
public final class DropFxConfig {

    /** 粒子注册 id（"namespace:path"；必须是无数据粒子类型才可播放，见引擎注释）。 */
    public String particleId = "";
    /** 该粒子的形状子配置列表（独立、可多个、叠加播放）。 */
    public final List<ShapePreset> shapes = new ArrayList<>();

    public DropFxConfig() {}

    public DropFxConfig(String particleId) {
        this.particleId = particleId;
    }

    /** 深拷贝（GUI 编辑副本用：编辑确定前不碰配方内存树）。 */
    public DropFxConfig copy() {
        DropFxConfig c = new DropFxConfig(this.particleId);
        for (ShapePreset p : this.shapes) c.shapes.add(p.copy());
        return c;
    }

    public CompoundTag toNBT() {
        CompoundTag t = new CompoundTag();
        t.putString("ParticleId", this.particleId == null ? "" : this.particleId);
        ListTag list = new ListTag();
        for (ShapePreset p : this.shapes) list.add(p.toNBT());
        t.put("Shapes", list);
        return t;
    }

    public static DropFxConfig fromNBT(CompoundTag t) {
        DropFxConfig c = new DropFxConfig(t.getString("ParticleId"));
        ListTag list = t.getList("Shapes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            c.shapes.add(ShapePreset.fromNBT(list.getCompound(i)).withDefaults());
        }
        return c;
    }
}
