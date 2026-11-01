package top.cnpcplus.mixin;

import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.TextContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GuiTextArea.class, remap = false)
public interface IGuiTextAreaScriptAccess {
    @Accessor("cursorPosition")
    int cnpcplus$cursor();

    @Accessor("container")
    TextContainer cnpcplus$container();

    @Accessor("scrolledLine")
    int cnpcplus$scroll();

    @Accessor("font")
    static noppes.npcs.shared.client.util.TrueTypeFont cnpcplus$font() { throw new AssertionError(); }

    @Accessor("scrolledLine")
    void cnpcplus$scroll(int line);

    @Invoker("setCursor")
    void cnpcplus$cursor(int index, boolean select);
}
