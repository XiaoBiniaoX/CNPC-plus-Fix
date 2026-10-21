package top.cnpcplus.transport.client;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * 玩家传送面板对外接口。混入 GuiTransportSelection 实现；
 * 行按钮/渲染经目标类→接口强转访问，禁止 mixin class 互转。
 */
public interface ITransportPanelUI {
    String cnpcplus$getSelected();
    void cnpcplus$setSelected(String name);
    List<String> cnpcplus$getVisibleNames();
    int cnpcplus$getOffset();
    void cnpcplus$setOffset(int offset);
    CompoundTag cnpcplus$getMeta(String name);
}
