package top.cnpcplus.transport.client;

public interface TextAreaSelectionAccess {
    void cnpcplus$clearSelection();

    /** 当前选区起点（用于鼠标拖选时保持锚点）。 */
    int cnpcplus$selectionStart();

    /** 鼠标坐标对应的字符下标（复用 CNPC 的换行/折行计算）。 */
    int cnpcplus$selectionPos(double mouseX, double mouseY);

    /** 直接设置选区 [start,end) 与光标位置。 */
    void cnpcplus$select(int start, int end, int cursor);
}
