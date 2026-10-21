package top.cnpcplus.transport;

/**
 * TransportLocation 混入扩展字段的访问接口。
 * 目标类→接口强转是 mixin 标准模式：((TransportVisualMeta)(Object)loc)。
 * 禁止 mixin class 互转。
 */
public interface TransportVisualMeta {
    String cnpcplus$getIcon();
    void cnpcplus$setIcon(String v);
    String cnpcplus$getIconSel();
    void cnpcplus$setIconSel(String v);
    String cnpcplus$getImage();
    void cnpcplus$setImage(String v);
    int cnpcplus$getImageMode();
    void cnpcplus$setImageMode(int v);
    String cnpcplus$getDesc();
    void cnpcplus$setDesc(String v);
}
