package top.cnpcplus.text.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.shared.client.util.NoppesStringUtils;
import org.joml.Matrix4f;
import top.cnpcplus.text.StyledText;

public final class StyledTextRender {
    private StyledTextRender() {}

    public static StyledText format(StyledText value, Object... obs) {
        String translated = NoppesStringUtils.translate(value.text());
        if (!translated.equals(value.text())) value = value.edit(translated);
        for (Object ob : obs) {
            if (ob instanceof Player player) {
                value = value.replaceAll("{player}", player.getName().getString());
                value = value.replaceAll("@p", player.getName().getString());
            } else if (ob instanceof EntityNPCInterface npc) value = value.replaceAll("@npc", npc.getName().getString());
        }
        return new StyledText(value.text().replace('&', '\u00a7'), value.spans()).legacy();
    }

    public static List<StyledText.Line> lines(StyledText text, int width, boolean mcFont) {
        Font font = Minecraft.getInstance().font;
        return text.lines(width, mcFont ? component -> font.width(component) : StyledTextRender::width);
    }

    /** CNPC's custom glyphs are unstyled; draw and measure share the same styled runs. */
    public static double width(Component text) {
        if (!ClientProxy.Font.useCustomFont) return Minecraft.getInstance().font.width(text);
        double[] width = {0};
        text.visit((style, segment) -> {
            width[0] += advance(segment, style);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return width[0];
    }

    private static int advance(String segment, Style style) {
        return segment.isEmpty() ? 0 : ClientProxy.Font.width(segment) + (style.isBold() ? 1 : 0) + (style.isItalic() ? 2 : 0);
    }

    public static void draw(GuiGraphics graphics, Component text, int x, int y, int baseColor, boolean mcFont) {
        if (mcFont || !ClientProxy.Font.useCustomFont) {
            graphics.drawString(Minecraft.getInstance().font, text, x, y, baseColor, false);
            return;
        }
        int[] px = {x};
        int height = ClientProxy.Font.height(null);
        text.visit((style, segment) -> {
            int color = style.getColor() == null ? baseColor : style.getColor().getValue();
            if (!segment.isEmpty()) {
                int width = advance(segment, style);
                graphics.pose().pushPose();
                graphics.pose().translate(px[0], y, 0);
                if (style.isItalic()) {
                    graphics.pose().translate(2, 0, 0);
                    graphics.pose().last().pose().mul(new Matrix4f().m10(-2F / Math.max(1, height)));
                }
                ClientProxy.Font.draw(graphics, segment, 0, 0, color);
                if (style.isBold()) ClientProxy.Font.draw(graphics, segment, 1, 0, color);
                graphics.pose().popPose();
                if (style.isUnderlined()) graphics.fill(px[0], y + height - 1, px[0] + width, y + height, color | 0xFF000000);
                px[0] += width;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
    }
}
