package top.cnpcplus;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogCategory;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.controllers.data.QuestCategory;
import noppes.npcs.shared.client.gui.components.TextContainer;
import noppes.npcs.shared.client.util.TrueTypeFont;
import top.cnpcplus.text.PreviewTextData;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.NativeTextLayout;

/** Real native line objects and NBT drafts, without a renderer, player or network. */
public final class TextRepairRegression {
    public static void main(String[] args) throws Exception {
        // AWT font measurement only. Native glyph texture creation requires an actual GL context.
        TrueTypeFont font = new TrueTypeFont(new Font("Dialog", Font.PLAIN, 18), 1) {
            @Override public int width(String text) { return text.codePointCount(0, text.length()) * 7; }
        };
        for (String text : List.of("", "甲乙中文😀末尾", "EnglishwordWithoutAnySpace", "首\n\n尾\n", "\r\n\n", "空白  保留")) {
            TextContainer container = new TextContainer(text);
            List<Object> lines = NativeTextLayout.lines(container, font, 31);
            StringBuilder joined = new StringBuilder();
            int lastEnd = 0;
            for (int i = 0; i < lines.size(); i++) {
                Object line = lines.get(i);
                String value = (String)field(line, "text");
                int start = (int)field(line, "start"), end = (int)field(line, "end");
                check(start == lastEnd && end > start, "native line gap/EOF slot");
                check(text.substring(start, start + value.length()).equals(value), "native source offset");
                check(font.width(value) <= 21, "long Chinese/word still overflows");
                check(NativeTextLayout.selectionPos(value, start, 99999, font) == start + value.length(), "line end lost final character");
                check(NativeTextLayout.selectionPos(value, start, -1, font) == start, "line start hit");
                for (double x = 0; x < 25; x += 0.5) {
                    int hit = NativeTextLayout.selectionPos(value, start, x, font);
                    check(hit >= start && hit <= start + value.length(), "hit out of bounds");
                    check(hit == text.length() || !Character.isLowSurrogate(text.charAt(hit)), "caret split emoji");
                }
                check(end == (i + 1 < lines.size() ? (int)field(lines.get(i + 1), "start") : text.length() + 1), "native end convention");
                joined.append(value);
                lastEnd = end;
            }
            check(joined.toString().equals(text.replace("\r", "").replace("\n", "")), "native layout lost characters");
        }

        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException)
                    || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
            System.out.println("HEADLESS_ONLY: untransformed Forge hook, not GUI/Mixin verification");
        }
        StyledText styled = new StyledText("预览中文😀", "").apply(0, 4, 0x12ABCD, 7, false);
        Dialog dialog = new Dialog(new DialogCategory());
        dialog.title = "source";
        dialog.text = "original";
        dialog.sound = "minecraft:entity.player.hurt";
        DialogOption option = new DialogOption();
        option.title = "option";
        dialog.options.put(0, option);
        Dialog preview = PreviewTextData.dialog(dialog, styled);
        check(preview != dialog && preview.text.equals(styled.text()) && preview.sound.isEmpty(), "dialog not detached/silent before construction");
        check(TextStyles.get(preview, TextStyles.DIALOG, preview.text).equals(styled), "dialog preview styles missing");
        preview.options.get(0).title = "preview edit";
        preview.mail.subject = "preview mail";
        check(dialog.options.get(0).title.equals("option") && !dialog.mail.subject.equals("preview mail"), "dialog NBT objects alias");
        check(dialog.text.equals("original") && !dialog.sound.isEmpty(), "dialog source changed by preview");

        QuestCategory category = new QuestCategory();
        category.title = "category";
        Quest quest = new Quest(category);
        quest.logText = "log";
        quest.completeText = "complete";
        TextStyles.put(quest, TextStyles.COMPLETE, new StyledText("complete", "").apply(0, 3, 15, 1, false));
        for (boolean log : List.of(true, false)) {
            Quest q = PreviewTextData.quest(quest, styled, log);
            check(q != quest && q.category != category && q.questInterface != quest.questInterface
                    && q.rewardItems != quest.rewardItems && q.mail != quest.mail, "quest mutable data alias");
            check((log ? q.logText : q.completeText).equals(styled.text()), "wrong preview text kind");
            check((log ? q.completeText : q.logText).equals(log ? "complete" : "log"), "other quest field changed");
            check(TextStyles.get(q, log ? TextStyles.LOG : TextStyles.COMPLETE, styled.text()).equals(styled), "quest preview styles missing");
            q.category.title = "preview";
            q.mail.subject = "preview mail";
            check(category.title.equals("category") && !quest.mail.subject.equals("preview mail"), "quest draft changed source");
        }
        System.out.println("TEXT_REPAIR_REGRESSION=PASS (native line objects/draft NBT, not GUI/Mixin loading)");
    }

    private static Object field(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
