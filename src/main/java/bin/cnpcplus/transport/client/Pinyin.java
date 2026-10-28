package bin.cnpcplus.transport.client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 与参考版共用汉字表，支持中文、完整拼音与拼音首字母模糊搜索。 */
public final class Pinyin {
    private static final Map<Character, String> TABLE = new HashMap<>();
    static {
        try (InputStream in = Pinyin.class.getResourceAsStream("/assets/cnpcplus/pinyin/hanzi.txt")) {
            if (in != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) if (line.length() >= 3 && line.charAt(1) == '\t') TABLE.put(line.charAt(0), line.substring(2));
            }
        } catch (java.io.IOException error) { bin.cnpcplus.CnpcPlus.LOGGER.error("Cannot read transport pinyin table", error); }
    }
    private Pinyin() { }
    public static String of(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) out.append(TABLE.getOrDefault(c, Character.toString(c)));
        return out.toString();
    }
    public static boolean matches(String query, String text) {
        query = query.toLowerCase(Locale.ROOT).replace(" ", "");
        return fuzzy(query, text.toLowerCase(Locale.ROOT)) || fuzzy(query, of(text).toLowerCase(Locale.ROOT));
    }
    private static boolean fuzzy(String query, String text) {
        int index = 0;
        for (int i = 0; i < text.length() && index < query.length(); i++) if (text.charAt(i) == query.charAt(index)) index++;
        return index == query.length();
    }
}
