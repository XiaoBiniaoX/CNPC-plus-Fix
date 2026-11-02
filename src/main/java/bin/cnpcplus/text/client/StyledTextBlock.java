package bin.cnpcplus.text.client;

import bin.cnpcplus.text.StyledText;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import noppes.npcs.NoppesStringUtils;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.TextBlockClient;

/** Native block list/name contract, with source-indexed rows and independent style snapshot. */
public final class StyledTextBlock extends TextBlockClient {
    private final ICommandSender speaker;
    public final StyledText value;
    public final List<StyledText.Row> rows;
    public final Object font;
    public StyledTextBlock(ICommandSender speaker, StyledText value, int width, int color, boolean mcFont, Object... context) {
        super("", width, mcFont); this.speaker = speaker; this.color = color;
        this.value = value.format(text -> NoppesStringUtils.formatText(text, context)).legacy();
        font = mcFont ? Minecraft.getMinecraft().fontRenderer : ClientProxy.Font;
        rows = this.value.rows(width - 3, text -> StyledTextRender.width(font, text));
        lines.clear();
        for (StyledText.Row row : rows) lines.add(new TextComponentString(row.text));
    }
    @Override public String getName() { return speaker == null ? "" : speaker.getName(); }
    public void draw(int index, int x, int y, int height, int color) {
        StyledText.Row row = rows.get(index);
        StyledTextRender.draw(font, value, row.start, row.end, x, y, height, color);
    }
}
