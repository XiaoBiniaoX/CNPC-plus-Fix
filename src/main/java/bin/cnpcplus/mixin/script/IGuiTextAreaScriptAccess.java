package bin.cnpcplus.mixin.script;

import noppes.npcs.client.gui.util.GuiTextArea;
import noppes.npcs.client.gui.util.TextContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GuiTextArea.class, remap = false)
public interface IGuiTextAreaScriptAccess {
    @Accessor("cursorPosition") int cnpcplus$cursor();
    @Accessor("container") TextContainer cnpcplus$container();
    @Accessor("scrolledLine") void cnpcplus$scroll(int line);
    @Invoker("addText") void cnpcplus$insert(String text);
    @Invoker("setCursor") void cnpcplus$cursor(int index, boolean select);
}
