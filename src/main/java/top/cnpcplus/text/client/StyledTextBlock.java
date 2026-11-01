package top.cnpcplus.text.client;

import noppes.npcs.client.TextBlockClient;
import net.minecraft.commands.CommandSourceStack;
import top.cnpcplus.text.StyledText;

/** Native chat history still owns its name, scrolling and colors. */
public final class StyledTextBlock extends TextBlockClient {
    public StyledTextBlock(String name, StyledText value, int width, int color, Object... obs) {
        super(name, "", width, color);
        lines.clear();
        for (var line : StyledTextRender.lines(StyledTextRender.format(value, obs), width, false)) lines.add(line.component());
    }

    public StyledTextBlock(CommandSourceStack sender, StyledText value, int width, int color, Object... obs) {
        super(sender, "", width, color);
        lines.clear();
        for (var line : StyledTextRender.lines(StyledTextRender.format(value, obs), width, false)) lines.add(line.component());
    }

    public StyledTextBlock(StyledText value, int width, boolean mcFont, Object... obs) {
        super("", width, mcFont);
        lines.clear();
        for (var line : StyledTextRender.lines(StyledTextRender.format(value, obs), width, mcFont)) lines.add(line.component());
    }
}
