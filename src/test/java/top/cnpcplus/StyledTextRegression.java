package top.cnpcplus;

import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.transport.TransportSpans;

/** Data/layout check only; real fonts, GUI selection and Mixin lifecycle need game verification. */
public final class StyledTextRegression {
    public static void main(String[] args) {
        StyledText source = new StyledText("甲乙中文😀末尾", "").apply(2, 6, 0x12ABCD, 7, false);
        check(source.text().equals("甲乙中文😀末尾") && !source.text().contains("§"), "style polluted source");
        check(style(source.component(), 2).isBold() && style(source.component(), 2).isUnderlined()
                && style(source.component(), 2).isItalic() && style(source.component(), 2).getColor().getValue() == 0x12ABCD,
                "component style lost");
        StyledText colorless = source.apply(2, 6, TransportSpans.CLEAR_COLOR, 0, false);
        check(style(colorless.component(), 2).getColor() == null && style(colorless.component(), 2).isBold(), "clear color removed flags");
        StyledText plain = colorless.apply(2, 6, -1, 0, true);
        check(!style(plain.component(), 2).isBold() && !style(plain.component(), 2).isUnderlined(), "reset format failed");
        check(!style(source.apply(2, 6, -1, 1, true).component(), 2).isBold(), "flag toggle failed");

        StyledText shifted = source.edit("前缀甲乙中文😀末尾");
        check(style(shifted.component(), 4).isBold() && !style(shifted.component(), 3).isBold(), "insert prefix offset");
        shifted = shifted.edit("前缀甲乙文😀末尾");
        check(style(shifted.component(), 4).isBold() && style(shifted.component(), 5).isBold()
                && !style(shifted.component(), 7).isBold(), "delete/Unicode offset");
        StyledText emoji = new StyledText("A😀Z", "").apply(1, 3, 123, 1, false).edit("A😁Z");
        check(emoji.text().equals("A😁Z") && style(emoji.component(), 1).isBold(), "surrogate replacement");
        StyledText placeholder = new StyledText("X {player} Y @p Z", "").apply(2, 10, 0x123456, 2, false)
                .apply(11, 12, 0xABCDEF, 1, false).replaceAll("{player}", "很长的玩家😀").replaceAll("@p", "Alex");
        check(placeholder.text().equals("X 很长的玩家😀 Y Alex Z"), "placeholder source");
        check(style(placeholder.component(), 2).isUnderlined()
                && style(placeholder.component(), placeholder.text().indexOf('Y')).isBold(), "placeholder style offsets");
        check(new StyledText("@p", "").replaceAll("@p", "@p").text().equals("@p"), "self-containing replacement loop");

        StyledText old = new StyledText("前§C红§L粗§r后§X§1§2§A§B§C§D色", "").legacy();
        check(old.text().equals("前红粗后色"), "legacy upper/lower/hex codes");
        check(style(old.component(), 1).getColor().getValue() == 0xFF5555 && style(old.component(), 2).isBold()
                && style(old.component(), 3).getColor() == null && style(old.component(), 4).getColor().getValue() == 0x12ABCD, "legacy format meaning");
        check(new StyledText("§q末§", "").legacy().text().equals("§q末§"), "unknown/dangling code removed");
        StyledText overlay = new StyledText("A§cBC", "4,5,1193046,2").legacy();
        check(style(overlay.component(), 2).getColor().getValue() == 0x123456
                && style(overlay.component(), 2).isUnderlined(), "external styles misaligned after legacy removal");
        check(old.equals(old.legacy()), "legacy conversion not idempotent");

        Object owner = new Object(), copy = new Object();
        TextStyles.put(owner, TextStyles.DIALOG, source);
        CompoundTag tag = new CompoundTag();
        TextStyles.save(owner, TextStyles.DIALOG, source.text(), tag);
        TextStyles.read(copy, TextStyles.DIALOG, source.text(), tag.copy());
        check(TextStyles.get(copy, TextStyles.DIALOG, source.text()).equals(source), "NBT reload");
        TextStyles.put(copy, TextStyles.DIALOG, plain);
        check(TextStyles.get(owner, TextStyles.DIALOG, source.text()).equals(source), "copy aliases mutable styles");
        TextStyles.read(copy, TextStyles.LOG, "old", new CompoundTag());
        check(TextStyles.get(copy, TextStyles.LOG, "old").spans().isEmpty(), "old missing style data");
        TextStyles.put(copy, TextStyles.COMPLETE, source);
        TextStyles.save(copy, TextStyles.COMPLETE, source.text(), tag);
        check(tag.contains(TextStyles.DIALOG) && tag.contains(TextStyles.COMPLETE), "keys collide");

        checkWrap(new StyledText("长中文😀超长EnglishwordWithoutAnySpace\r\n空白  保留\n\n尾\n", "").apply(0, 20, 15, 7, false), 8);
        check(new StyledText("\r\n\n", "").lines(10, StyledTextRegression::measure).size() == 3, "explicit empty lines");
        Random random = new Random(4067);
        String[] glyphs = {"甲", "😀", "x", " ", "\r\n", "\n", "é"};
        for (int i = 0; i < 500; i++) {
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < 150; j++) text.append(glyphs[random.nextInt(glyphs.length)]);
            checkWrap(new StyledText(text.toString(), "").apply(0, text.length(), 0xFFAA00, 1, false), 1 + random.nextInt(20));
        }
        System.out.println("STYLED_TEXT_REGRESSION=PASS");
    }

    private static void checkWrap(StyledText text, int width) {
        StringBuilder joined = new StringBuilder();
        for (StyledText.Line line : text.lines(width, StyledTextRegression::measure)) {
            String shown = line.component().getString();
            check(text.text().substring(line.start(), line.end()).equals(shown), "source-indexed lines drift");
            check(shown.isEmpty() || !Character.isLowSurrogate(shown.charAt(0)), "wrapped inside surrogate");
            check(line.width() <= width || shown.codePointCount(0, shown.length()) == 1, "line exceeds measured width");
            joined.append(shown);
        }
        check(joined.toString().equals(text.text().replace("\r", "").replace("\n", "")), "wrapping lost text/spaces");
    }

    private static double measure(Component component) {
        double[] value = {0};
        component.visit((style, text) -> {
            value[0] += text.codePointCount(0, text.length()) * (style.isBold() ? 2 : 1);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return value[0];
    }

    private static Style style(Component component, int index) {
        int[] at = {0};
        Style[] found = {Style.EMPTY};
        component.visit((style, text) -> {
            if (index >= at[0] && index < at[0] + text.length()) found[0] = style;
            at[0] += text.length();
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
