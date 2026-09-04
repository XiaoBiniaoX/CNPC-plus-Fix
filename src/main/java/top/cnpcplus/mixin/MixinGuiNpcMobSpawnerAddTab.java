package top.cnpcplus.mixin;

import noppes.npcs.client.gui.GuiNpcMobSpawnerAdd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 修复复制魔杖保存界面「同名覆盖提示查错了分页」。
 *
 * <p>根因（原版源码 + 字节码双实证）：buttonEvent 里有一个局部变量遮蔽了静态字段
 * <pre>int tab = guibutton.getValue() + 1;   // 字节码 offset 26-32，istore 4</pre>
 * 而这里的 {@code guibutton} 是**保存按钮**（id 0），它由不带 display 数组的构造函数创建，
 * {@code GuiButtonNop.getValue()} 返回的 displayValue 恒为 0 —— 于是这个局部 tab 恒等于 1。
 *
 * <p>真正记录用户所选分页的是静态字段 tab（offset 118-124，只在按钮 id==2 时 putstatic），
 * 而 {@code accept()} 落盘时用的正是这个静态字段。结果：
 * <ul>
 *   <li>存在性检查永远问「Tab 1 里有没有这个名字」</li>
 *   <li>实际写入却落在用户选择的分页</li>
 * </ul>
 * 用户选了 Tab 3 覆盖同名克隆时，Tab 1 没有该名字 → 不弹确认框、直接静默保存；
 * 反之 Tab 1 恰好有同名时，又会对一个其实空闲的名字弹出多余的覆盖确认。
 * 加上 tab 是 static、跨界面开启持续存在，行为看起来是随机的。
 *
 * <p>修法：把那个局部变量改写成静态字段的真实值，让检查与写入用同一个分页。
 * 只改一个局部变量，不动按钮布局、不动包结构、不动落盘逻辑。
 *
 * <p>index=4 是该局部变量在 LVT 中的槽位（this=0, guibutton=1, id=2, name=3, tab=4），
 * 已由 javap 字节码 {@code istore 4} 确认；用槽位而非变量名，不依赖调试信息是否保留。
 */
@Mixin(value = GuiNpcMobSpawnerAdd.class, remap = false)
public class MixinGuiNpcMobSpawnerAddTab {

    @Shadow(remap = false)
    private static int tab;

    @ModifyVariable(method = "buttonEvent", at = @At("STORE"), index = 4, remap = false)
    private int cnpcplus$useSelectedTab(int shadowedAlwaysOne) {
        return tab;
    }
}
