package top.cnpcplus.drop.fx.runtime;

import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.ShapePreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次合成的特效实例（233d）：合成触发时由 {@link DropFxEngine.spawn} 创建，
 * 持有配方 fx 树的<b>不可变快照</b>（深拷贝——配方后续被 GUI 编辑不影响已播放实例）。
 * 生命周期：起始 tick 起至 maxDuration 结束，由引擎每 tick 驱动发送粒子。
 */
public final class DropFxInstance {

    /** 合成掉落物中心（方块中心+0.2，与产出 ItemEntity 一致）。 */
    public final double cx, cy, cz;
    /** fx 快照（每个 config 的 shapes 已深拷贝且经 Caps 裁剪）。 */
    public final List<DropFxConfig> fxs;
    public final long startTick;
    /** 本次实例的最大时长（所有形状 duration 的最大值，提前算好省遍历）。 */
    public final int maxDuration;

    public DropFxInstance(double cx, double cy, double cz,
                          List<DropFxConfig> sourceFxs, long startTick) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
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
}
