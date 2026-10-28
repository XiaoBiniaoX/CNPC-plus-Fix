package top.cnpcplus.drop.fx.runtime;

import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.ShapePreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 一次合成的特效实例（233d）：合成触发时由 {@link DropFxEngine.spawn} 创建，
 * 持有配方 fx 树的<b>不可变快照</b>（深拷贝——配方后续被 GUI 编辑不影响已播放实例）。
 * 生命周期：起始 tick 起至 maxDuration 结束，由引擎每 tick 驱动发送粒子。
 */
public final class DropFxInstance {

    /** 合成触发掉落物实体位置（233d-UI6：用户要求粒子以掉落物世界坐标发射，非方块中心）。 */
    public final double cx, cy, cz;
    /** fx 快照（每个 config 的 shapes 已深拷贝且经 Caps 裁剪）。 */
    public final List<DropFxConfig> fxs;
    public final long startTick;
    /** 本次实例的最大时长（所有形状 duration 的最大值，提前算好省遍历）。 */
    public final int maxDuration;
    /** null=原周期钉地；非null=同一实例循环、实时实体位置生成。 */
    public final Entity anchor;

    public DropFxInstance(double cx, double cy, double cz,
                          List<DropFxConfig> sourceFxs, long startTick) {
        this(cx, cy, cz, sourceFxs, startTick, null);
    }

    public DropFxInstance(double cx, double cy, double cz,
                          List<DropFxConfig> sourceFxs, long startTick, Entity anchor) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.anchor = anchor;
        List<DropFxConfig> snap = new ArrayList<>(sourceFxs.size());
        int maxDur = 0;
        for (DropFxConfig c : sourceFxs) {
            DropFxConfig cc = c.copy();
            for (ShapePreset p : cc.shapes) {
                p.withDefaults();
                maxDur = Math.max(maxDur, p.durationTicks);
            }
            snap.add(cc);
        }
        this.fxs = Collections.unmodifiableList(snap);
        this.startTick = startTick;
        this.maxDuration = Math.max(1, maxDur);
    }

    public Vec3 center() {
        return this.anchor == null ? new Vec3(this.cx, this.cy, this.cz) : this.anchor.position();
    }

    public long shapeAge(long age, ShapePreset shape) {
        return this.anchor == null ? age : age % Math.max(1, shape.durationTicks);
    }
}
