package top.cnpcplus.transport;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务241q：文本样式区间（颜色/粗体/下划线/斜体）。
 *
 * <p>241q 起因：之前把颜色当成"整段字符串的属性"，或用 MC 格式码（§x§C§C…）写进正文
 * → 正文里出现可见的乱码，且改颜色会改到整段。
 * 现在：正文保持干净，样式单独存成区间列表（每项 = 起始,结束,RGB,标志位）。
 *
 * <p>存储格式（半角分号分隔）：{@code s,e,rgb,flags;s,e,rgb,flags}
 * rgb = -1 表示"不覆盖颜色"（只改粗/斜/下划线）。
 * flags 位：1=粗体 2=下划线 4=斜体。
 */
public final class TransportSpans {
    public static final int FLAG_BOLD = 1;
    public static final int FLAG_UNDERLINE = 2;
    public static final int FLAG_ITALIC = 4;

    private TransportSpans() {}

    public static final class Span {
        public int start;
        public int end;
        public int rgb;
        public int flags;

        public Span(int start, int end, int rgb, int flags) {
            this.start = start;
            this.end = end;
            this.rgb = rgb;
            this.flags = flags;
        }
    }

    public static List<Span> parse(String raw) {
        List<Span> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(";")) {
            if (part.isEmpty()) continue;
            String[] f = part.split(",");
            if (f.length < 4) continue;
            try {
                int s = Integer.parseInt(f[0].trim());
                int e = Integer.parseInt(f[1].trim());
                int c = Integer.parseInt(f[2].trim());
                int fl = Integer.parseInt(f[3].trim());
                if (e > s) out.add(new Span(s, e, c, fl));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static String serialize(List<Span> spans) {
        if (spans == null || spans.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Span s : spans) {
            if (sb.length() > 0) sb.append(';');
            sb.append(s.start).append(',').append(s.end).append(',').append(s.rgb).append(',').append(s.flags);
        }
        return sb.toString();
    }

    /**
     * 在 [start,end) 上应用样式。
     *
     * @param rgb   >=0 覆盖颜色；-1 = 不改颜色
     * @param flags 要置位的标志（0 = 不改）
     * @param toggle true = 切换该标志（原来有就去掉）
     */
    public static String apply(String raw, int start, int end, int rgb, int flags, boolean toggle) {
        if (end <= start) return raw == null ? "" : raw;
        List<Span> spans = parse(raw);
        // 已有区间在本次范围上的部分：颜色被覆盖的先拆掉
        List<Span> next = new ArrayList<>();
        for (Span s : spans) {
            if (s.end <= start || s.start >= end) {
                next.add(s);
                continue;
            }
            if (rgb >= 0) {
                if (s.start < start) next.add(new Span(s.start, start, s.rgb, s.flags));
                if (s.end > end) next.add(new Span(end, s.end, s.rgb, s.flags));
            } else {
                next.add(s);
            }
        }
        int newFlags = flags;
        if (toggle) {
            // 该范围内是否已全部带该标志 → 若是则取消
            boolean all = true;
            boolean any = false;
            for (Span s : next) {
                if (s.start < end && s.end > start) {
                    any = true;
                    if ((s.flags & flags) == 0) { all = false; break; }
                }
            }
            if (all && any) newFlags = 0;
        }
        next.add(new Span(start, end, rgb, newFlags));
        return serialize(next);
    }

    /** 取第 index 个字符的颜色（-1 = 用基础色）。 */
    /**
     * 241n：把正文里**遗留的旧格式码**（早期版本直接写进正文的 §x 十六进制颜色码、§l/§n/§o）
     * 解析出来转成新的样式区间 → 正文干净、颜色与格式保留（用户拍板：自动转换，不删）。
     *
     * @return [清洗后的正文, 样式区间字符串]
     */
    public static String[] fromLegacy(String text) {
        if (text == null || text.isEmpty()) return new String[]{text == null ? "" : text, ""};
        if (text.indexOf('\u00a7') < 0) return new String[]{text, ""};

        StringBuilder clean = new StringBuilder();
        List<int[]> events = new ArrayList<>();   // {位置, 颜色(-1=继承), 标志}
        int curColor = -1;
        int curFlags = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c != '\u00a7' || i + 1 >= n) {
                clean.append(c);
                i++;
                continue;
            }
            char k = text.charAt(i + 1);
            if (k == 'x') {
                int[] rgb = parseHexColor(text, i);
                if (rgb != null) {
                    curColor = rgb[0];
                    events.add(new int[]{clean.length(), curColor, curFlags});
                    i += 14;
                    continue;
                }
            }
            if (k == 'r') {
                curColor = -1;
                curFlags = 0;
                events.add(new int[]{clean.length(), -1, 0});
                i += 2;
                continue;
            }
            if (k == 'l' || k == 'n' || k == 'o') {
                curFlags |= (k == 'l' ? FLAG_BOLD : (k == 'n' ? FLAG_UNDERLINE : FLAG_ITALIC));
                events.add(new int[]{clean.length(), curColor, curFlags});
                i += 2;
                continue;
            }
            clean.append(c);
            i++;
        }

        List<Span> spans = new ArrayList<>();
        int start = 0;
        int color = -1;
        int flags = 0;
        for (int[] e : events) {
            if (e[0] > start && (color >= 0 || flags != 0)) {
                spans.add(new Span(start, e[0], color, flags));
            }
            start = e[0];
            color = e[1];
            flags = e[2];
        }
        if (clean.length() > start && (color >= 0 || flags != 0)) {
            spans.add(new Span(start, clean.length(), color, flags));
        }
        return new String[]{clean.toString(), serialize(spans)};
    }

    /** 解析 §x§R§R§G§G§B§B 形式的十六进制颜色，返回 {rgb} 或 null。 */
    private static int[] parseHexColor(String text, int pos) {
        int[] digits = new int[6];
        int p = pos + 2;
        for (int i = 0; i < 6; i++) {
            if (p + 1 >= text.length() || text.charAt(p) != '\u00a7') return null;
            int v = Character.digit(text.charAt(p + 1), 16);
            if (v < 0) return null;
            digits[i] = v;
            p += 2;
        }
        int rgb = (digits[0] << 20) | (digits[1] << 16) | (digits[2] << 12)
                | (digits[3] << 8) | (digits[4] << 4) | digits[5];
        return new int[]{rgb & 0xFFFFFF};
    }

    /**
     * 241n：就地清理一个 meta 里的遗留旧格式码（正文 + 样式区间合并）。
     * 处理 Desc（每条目）与页签文本（面板级）。
     */
    public static void cleanLegacy(net.minecraft.nbt.CompoundTag meta) {
        if (meta == null) return;
        top.cnpcplus.transport.TransportKeys keys = null;
        String desc = meta.getString(top.cnpcplus.transport.TransportKeys.DESC);
        if (desc.indexOf('\u00a7') >= 0) {
            String[] r = fromLegacy(desc);
            meta.putString(top.cnpcplus.transport.TransportKeys.DESC, r[0]);
            String merged = merge(r[1], meta.getString(top.cnpcplus.transport.TransportKeys.DESC_SPANS));
            meta.putString(top.cnpcplus.transport.TransportKeys.DESC_SPANS, merged);
        }
        String tab = meta.getString(top.cnpcplus.transport.TransportPanelStyle.TAB_TEXT);
        if (tab.indexOf('\u00a7') >= 0) {
            String[] r = fromLegacy(tab);
            meta.putString(top.cnpcplus.transport.TransportPanelStyle.TAB_TEXT, r[0]);
            meta.putString(top.cnpcplus.transport.TransportPanelStyle.TAB_SPANS,
                    merge(r[1], meta.getString(top.cnpcplus.transport.TransportPanelStyle.TAB_SPANS)));
        }
    }

    /** 合并两串区间（同一文本上的两组样式，后者覆盖前者的颜色）。 */
    public static String merge(String a, String b) {
        if (a == null || a.isEmpty()) return b == null ? "" : b;
        if (b == null || b.isEmpty()) return a;
        List<Span> list = new ArrayList<>(parse(a));
        for (Span s : parse(b)) {
            list.removeIf(x -> x.start < s.end && x.end > s.start && x.rgb >= 0);
            list.add(s);
        }
        return serialize(list);
    }

    /** 取第 index 个字符的颜色（-1 = 用基础色）。 */
    public static int colorAt(List<Span> spans, int index, int fallback) {
        int rgb = fallback;
        if (spans != null) {
            for (Span s : spans) {
                if (index >= s.start && index < s.end && s.rgb >= 0) rgb = s.rgb;
            }
        }
        return rgb;
    }

    /** 取第 index 个字符的标志位。 */
    public static int flagsAt(List<Span> spans, int index) {
        int f = 0;
        if (spans != null) {
            for (Span s : spans) {
                if (index >= s.start && index < s.end) f |= s.flags;
            }
        }
        return f;
    }
}
