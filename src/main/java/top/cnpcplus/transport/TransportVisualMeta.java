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
    int cnpcplus$getIconSize();
    void cnpcplus$setIconSize(int v);
    int cnpcplus$getImageZoom();
    void cnpcplus$setImageZoom(int v);
    int cnpcplus$getTextColor();
    void cnpcplus$setTextColor(int v);
    int cnpcplus$getTextSize();
    void cnpcplus$setTextSize(int v);
    int cnpcplus$getImgX();
    void cnpcplus$setImgX(int v);
    int cnpcplus$getImgY();
    void cnpcplus$setImgY(int v);
    int cnpcplus$getImgScale();
    void cnpcplus$setImgScale(int v);
    int cnpcplus$getSrcW();
    void cnpcplus$setSrcW(int v);
    int cnpcplus$getSrcH();
    void cnpcplus$setSrcH(int v);
    String cnpcplus$getDescSpans();
    void cnpcplus$setDescSpans(String v);
    String cnpcplus$getNameSpans();
    void cnpcplus$setNameSpans(String v);
}
