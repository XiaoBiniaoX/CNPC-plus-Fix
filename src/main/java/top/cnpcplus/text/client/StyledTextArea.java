package top.cnpcplus.text.client;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.util.TrueTypeFont;
import org.joml.Matrix4f;
import top.cnpcplus.transport.client.TextAreaSelectionAccess;
import top.cnpcplus.mixin.TextLineAccess;
import top.cnpcplus.text.StyledText;

/** Native editing, hit-testing and selection; only glyph drawing receives styles. */
public final class StyledTextArea extends GuiTextArea {
    private final Supplier<StyledText> value;
    public StyledTextArea(int id, int x, int y, int width, int height, StyledText initial, Supplier<StyledText> value) {
        super(id, x, y, width, height, initial.text());
        this.value = value;
    }
    public void drawLine(GuiGraphics graphics, TrueTypeFont font, int x, int y, int baseColor) {
        TextAreaSelectionAccess access = (TextAreaSelectionAccess) (Object) this;
        var container = access.cnpcplus$container();
        int row = access.cnpcplus$scroll() + (y - this.y - 1) / container.lineHeight;
        if (row < 0 || row >= container.lines.size()) return;
        TextLineAccess line = (TextLineAccess) container.lines.get(row);
        StyledText styled = value.get();
        int start = line.cnpcplus$start(), end = start + line.cnpcplus$text().length();
        if (styled == null || !styled.text().equals(getText())) styled = new StyledText(getText(), "");
        int[] at = {start};
        styled.component(start, end).visit((style, segment) -> {
            int left = font.width(getText().substring(start, at[0]));
            at[0] += segment.length();
            int right = font.width(getText().substring(start, at[0]));
            int color = style.getColor() == null ? baseColor : style.getColor().getValue();
            graphics.pose().pushPose();
            graphics.pose().translate(x + left, y, 0);
            if (style.isItalic()) {
                graphics.pose().translate(2, 0, 0);
                graphics.pose().last().pose().mul(new Matrix4f().m10(-2F / container.lineHeight));
            }
            font.draw(graphics.pose(), segment, 0, 0, color);
            if (style.isBold()) font.draw(graphics.pose(), segment, 1, 0, color);
            graphics.pose().popPose();
            if (style.isUnderlined()) graphics.fill(x + left, y + container.lineHeight - 1, x + right, y + container.lineHeight, color | 0xFF000000);
            return java.util.Optional.empty();
        }, Style.EMPTY);
    }
}
