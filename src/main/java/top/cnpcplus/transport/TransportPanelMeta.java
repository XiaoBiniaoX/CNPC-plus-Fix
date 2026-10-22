package top.cnpcplus.transport;

import net.minecraft.nbt.CompoundTag;

/**
 * TransportCategory 混入扩展：面板样式 CompoundTag（cnpcplusPanel）。
 * 目标类→接口强转：((TransportPanelMeta)(Object)category)。
 */
public interface TransportPanelMeta {
    CompoundTag cnpcplus$panel();
    void cnpcplus$setPanel(CompoundTag tag);
}
