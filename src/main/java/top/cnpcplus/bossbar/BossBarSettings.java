package top.cnpcplus.bossbar;

import net.minecraft.nbt.CompoundTag;

public record BossBarSettings(int mode, int rgb) {
    public static final String KEY = "CNPCPlusBossbar";
    private static final int[] LEGACY_COLORS = {0xFF55FF, 0x5555FF, 0xFF5555, 0x55FF55, 0xFFFF55, 0xAA00AA, 0xFFFFFF};

    public BossBarSettings {
        mode = Math.max(0, Math.min(2, mode));
        rgb &= 0xFFFFFF;
    }

    public static BossBarSettings read(CompoundTag tag) {
        if (tag.contains(KEY, 10)) {
            CompoundTag settings = tag.getCompound(KEY);
            return new BossBarSettings(settings.getInt("Mode"), settings.getInt("RGB"));
        }
        return new BossBarSettings(tag.getInt("BossBar"), legacyColor(tag.getInt("BossColor")));
    }

    public static int legacyColor(int index) {
        return LEGACY_COLORS[Math.max(0, Math.min(LEGACY_COLORS.length - 1, index))];
    }

    public void write(CompoundTag tag) {
        CompoundTag settings = new CompoundTag();
        settings.putInt("Mode", mode);
        settings.putInt("RGB", rgb);
        tag.put(KEY, settings);
    }

    public static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6;
        float s = Math.max(0, Math.min(1, saturation)), v = Math.max(0, Math.min(1, value));
        float p = v * (1 - s), q = v * (1 - s * (h - (int) h)), t = v * (1 - s * (1 - h + (int) h));
        float[] channels = switch ((int) h) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
        return Math.round(channels[0] * 255) << 16 | Math.round(channels[1] * 255) << 8 | Math.round(channels[2] * 255);
    }

    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        float hue = delta == 0 ? 0 : max == r ? ((g - b) / delta + 6) % 6 : max == g ? (b - r) / delta + 2 : (r - g) / delta + 4;
        return new float[]{hue / 6, max == 0 ? 0 : delta / max, max};
    }
}
