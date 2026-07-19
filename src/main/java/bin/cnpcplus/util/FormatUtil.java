package bin.cnpcplus.util;

import net.minecraft.ChatFormatting;

public final class FormatUtil {
    private FormatUtil() {}

    public static String toSection(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace('&', '\u00a7');
    }

    public static boolean hasFormatCodes(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if (c == '&' || c == '\u00a7') {
                char next = Character.toLowerCase(text.charAt(i + 1));
                if ("0123456789abcdefklmnor".indexOf(next) >= 0) return true;
            }
        }
        return false;
    }

    public static String applyDefault(String text, String format) {
        if (text == null) text = "";
        if (format == null || format.isEmpty()) return toSection(text);
        if (hasFormatCodes(text)) return toSection(text);
        return toSection(format + text);
    }

    public static int parseHexColor(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        try {
            if (s.length() == 6) {
                return Integer.parseInt(s, 16) & 0xFFFFFF;
            }
            if (s.length() == 8) {
                return Integer.parseInt(s, 16);
            }
            if (s.length() <= 6) {
                return Integer.parseInt(s, 16) & 0xFFFFFF;
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    public static int firstColorOr(String format, int fallback) {
        if (format == null || format.isEmpty()) return fallback;
        String s = toSection(format);
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) != '\u00a7') continue;
            char code = Character.toLowerCase(s.charAt(i + 1));
            ChatFormatting fmt = ChatFormatting.getByCode(code);
            if (fmt != null && fmt.isColor() && fmt.getColor() != null) {
                return fmt.getColor();
            }
        }
        return fallback;
    }

    public static int resolveColor(int hexColor, String format, int fallback) {
        if (hexColor != fallback && hexColor != 0) {
            return hexColor & 0xFFFFFF;
        }
        return firstColorOr(format, fallback);
    }
}
