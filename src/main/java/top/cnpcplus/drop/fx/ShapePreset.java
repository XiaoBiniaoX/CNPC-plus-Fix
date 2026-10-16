package top.cnpcplus.drop.fx;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * 单个形状预设子配置（233d，粒子框架数据层）。
 * 归属关系：一条掉落物配方 → 多个 {@link DropFxConfig}（粒子）→ 每粒子多个本对象（形状）。
 * 不同粒子的预设互不影响；同一粒子的多个形状叠加播放。
 *
 * <p><b>数值语义</b>（全部输入框步进 0.01；用户拍板的范围例外见各字段）：
 * <ul>
 *   <li>{@link #radius}：形状整体缩放，负值取绝对值；</li>
 *   <li>{@link #rotSpeed}：每 tick 旋转弧度，负值=反方向（运行时相位 = rotation + rotSpeed*t）；</li>
 *   <li>{@link #rotation}：初始角度（度）；</li>
 *   <li>{@link #inset}+{@link #insetAmount}：内陷(true)/外扩(false)模式与量，
 *       沿「中心→采样点」方向偏移 amount*0.1*radius；</li>
 *   <li>{@link #durationTicks}：显示时长（tick），范围 1..1200（物理裁剪见 DropFxCaps）；</li>
 *   <li>{@link #offsetX/Y/Z}：以合成掉落物为中心的 XYZ 偏移；</li>
 *   <li>{@link #density}：密集程度，决定采样点数 clamp(round(density*16),1,64)。</li>
 * </ul>
 *
 * <p><b>发光（glow）已阉割</b>：1.20.1 vanilla 无逐粒子 emissive API，2026-10-17 用户确认
 * 直接去掉该参数，待后续方案（自定义着色器/资源包叠加粒子）再加回——此处不留字段占位，
 * 避免半成品接口被误用；新增时在 ShapePreset+GUI+Caps 三处同步补。
 *
 * <p><b>值相等</b>：手写 equals/hashCode（全部字段参与）——{@link DropFxSampler} 的点集缓存
 * 以本对象为键，参数任一变动即视为新预设重新采样。
 */
public final class ShapePreset {

    public ShapeType type = ShapeType.CIRCLE;
    public double radius = 1.0;
    public double rotSpeed = 0.0;
    public double rotation = 0.0;
    public boolean inset = false;
    public double insetAmount = 0.0;
    public int durationTicks = 60;
    public double offsetX = 0.0;
    public double offsetY = 0.5;
    public double offsetZ = 0.0;
    public double density = 1.0;

    public ShapePreset copy() {
        ShapePreset p = new ShapePreset();
        p.type = this.type;
        p.radius = this.radius;
        p.rotSpeed = this.rotSpeed;
        p.rotation = this.rotation;
        p.inset = this.inset;
        p.insetAmount = this.insetAmount;
        p.durationTicks = this.durationTicks;
        p.offsetX = this.offsetX;
        p.offsetY = this.offsetY;
        p.offsetZ = this.offsetZ;
        p.density = this.density;
        return p;
    }

    public CompoundTag toNBT() {
        CompoundTag t = new CompoundTag();
        t.putString("Type", this.type.name());
        t.putDouble("Radius", this.radius);
        t.putDouble("RotSpeed", this.rotSpeed);
        t.putDouble("Rotation", this.rotation);
        t.putBoolean("Inset", this.inset);
        t.putDouble("InsetAmt", this.insetAmount);
        t.putInt("Duration", this.durationTicks);
        t.putDouble("Ox", this.offsetX);
        t.putDouble("Oy", this.offsetY);
        t.putDouble("Oz", this.offsetZ);
        t.putDouble("Density", this.density);
        return t;
    }

    public static ShapePreset fromNBT(CompoundTag t) {
        ShapePreset p = new ShapePreset();
        try {
            p.type = ShapeType.valueOf(t.getString("Type"));
        } catch (IllegalArgumentException ex) {
            p.type = ShapeType.CIRCLE; // 未知形状名（版本漂移/手改 dat）→ 回退圆形
        }
        p.radius = t.getDouble("Radius");
        p.rotSpeed = t.getDouble("RotSpeed");
        p.rotation = t.getDouble("Rotation");
        p.inset = t.getBoolean("Inset");
        p.insetAmount = t.getDouble("InsetAmt");
        p.durationTicks = t.getInt("Duration");
        p.offsetX = t.getDouble("Ox");
        p.offsetY = t.getDouble("Oy");
        p.offsetZ = t.getDouble("Oz");
        p.density = t.getDouble("Density");
        return p;
    }

    /** NBT 缺字段时 getInt/getDouble 返回 0——老数据补默认（duration 至少 1、density 至少 0.01）。 */
    public ShapePreset withDefaults() {
        if (this.durationTicks <= 0) this.durationTicks = 60;
        if (this.density <= 0) this.density = 1.0;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShapePreset p)) return false;
        return type == p.type
                && Double.compare(radius, p.radius) == 0
                && Double.compare(rotSpeed, p.rotSpeed) == 0
                && Double.compare(rotation, p.rotation) == 0
                && inset == p.inset
                && Double.compare(insetAmount, p.insetAmount) == 0
                && durationTicks == p.durationTicks
                && Double.compare(offsetX, p.offsetX) == 0
                && Double.compare(offsetY, p.offsetY) == 0
                && Double.compare(offsetZ, p.offsetZ) == 0
                && Double.compare(density, p.density) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, radius, rotSpeed, rotation, inset, insetAmount,
                durationTicks, offsetX, offsetY, offsetZ, density);
    }
}
