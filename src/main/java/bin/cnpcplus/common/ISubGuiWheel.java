package bin.cnpcplus.common;

/**
 * CNPC 的子界面收不到滚轮事件：父界面把鼠标点击转发给 subgui，
 * 但 handleMouseInput 只作用于 mc.currentScreen（也就是父界面本身），
 * 而 GuiNPCInterface 及其父类都没有声明这个方法，无法注入。
 * 这里由 GuiScreen 的 mixin 统一把滚轮转发给实现了本接口的子界面。
 */
public interface ISubGuiWheel {
    /** delta 为滚轮方向（正为向上）；eventX/eventY 为 LWJGL 原始事件坐标，实现方自行换算。 */
    void cnpcplus$wheel(int delta, int eventX, int eventY);
}
