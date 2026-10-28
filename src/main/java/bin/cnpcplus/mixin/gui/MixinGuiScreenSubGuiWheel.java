package bin.cnpcplus.mixin.gui;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把滚轮从父界面转发给 CNPC 子界面（粒子编辑器等长列表的滚动）。
 *
 * 现象：子界面里滚轮完全没反应。
 *
 * 根因（证据，javap 于 CustomNPCs 1.12.2 jar + MCP 映射 jar）：
 * 1. {@code GuiNPCInterface.func_73864_a} 与 {@code GuiContainerNPCInterface.func_73864_a}
 *    开头都是 {@code subgui != null → subgui.func_73864_a(...)} 然后跳过自己的控件；
 *    点击转发是通的。
 * 2. 滚轮走 {@code func_146274_d}（handleMouseInput），只由 {@code mc.currentScreen}
 *    收到；两个父类与 SubGuiInterface 都**没有声明**它，所以子界面里覆盖它永远不会被调用。
 *
 * 为什么是这个形状（踩过的两个坑，都是真崩溃）：
 * - 放进 DEFAULT 阶段（mixins.cnpcplus.json）：GuiScreen 在 mod 构造期间就被别的 coremod
 *   加载了，prepare 直接抛 MixinTargetAlreadyLoadedException —— 客户端开窗即崩。
 * - 放进 early 阶段但 handler 里写 {@code instanceof GuiNPCInterface}：early 阶段应用
 *   Mixin 时 mod jar 还没进 LaunchClassLoader，预处理直接
 *   ClassNotFoundException: noppes.npcs.client.gui.util.GuiNPCInterface —— 同样开窗即崩。
 *
 * 所以：这条必须留在 early 阶段（它是 GuiScreen 唯一的活路），handler 体内
 * **一个 mod 类型都不能引用**，全部走反射 + 字符串。getSubGui() 是 CNPC 自有方法名、
 * cnpcplus$wheel 是本 mod 方法名，两者都不参与 MC 重映射，反射是稳定的。
 */
@Mixin(value = GuiScreen.class, remap = false)
public class MixinGuiScreenSubGuiWheel {

    @Inject(method = "func_146274_d()V", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$forwardWheel(CallbackInfo ci) {
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        // 仍在 GuiScreen.func_146269_k() 的 while (Mouse.next()) 循环内，
        // getEventDWheel() 就是当前事件的滚轮量，不会粘滞也不会被重复消费。
        try {
            Object screen = (Object) this;
            Object sub = screen.getClass().getMethod("getSubGui").invoke(screen);
            if (sub == null) return;
            sub.getClass()
                    .getMethod("cnpcplus$wheel", int.class, int.class, int.class)
                    .invoke(sub, wheel, Mouse.getEventX(), Mouse.getEventY());
        } catch (Throwable ignored) {
            // 父界面没有子界面、子界面不实现 cnpcplus$wheel —— 都是正常情况，静默跳过。
            // 这里也不能 log：early 阶段加载不了本 mod 的 logger。
        }
    }
}
