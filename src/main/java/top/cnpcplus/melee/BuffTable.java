package top.cnpcplus.melee;

import java.util.Map;

/**
 * BUFF 表的实例级操作接口——让 {@link SubGuiMeleeBuffs} 一份界面同时服务
 * 近战（{@code DataMelee}）与弹射物（{@code DataRanged}）两张平行表。
 *
 * <h3>为什么需要接口</h3>
 * 近战与弹射物的 BUFF 数据各自挂在不同的 CNPC data 对象上
 * （{@code DataMelee} / {@code DataRanged}，两者**无共享接口方法**），
 * 但多选界面的交互完全一致。用接口把「键是谁」留在实现侧，
 * 界面只持有四个操作，避免复制一份 300 行 GUI。
 *
 * <p>注册表解析（{@code resolve/nameOf}）、夹值（{@code clamp*}）等
 * 不依赖键类型的纯函数仍走 {@link MeleeBuffStore} 的静态方法，不在接口里。
 */
public interface BuffTable {

    /** 取实表（必要时创建），可直接改。 */
    Map<String, int[]> get();

    void put(String registryName, int amplifier, int seconds);

    void remove(String registryName);

    void clear();
}
