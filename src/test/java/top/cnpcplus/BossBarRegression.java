package top.cnpcplus;

import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import top.cnpcplus.bossbar.BossBarSettings;

/** Headless migration/color checks; GUI focus and HUD packets still need game verification. */
public final class BossBarRegression {
    public static void main(String[] args) {
        check(BossBarSettings.read(new CompoundTag()).mode() == 0, "new NPC boss bar enabled");
        for (int mode = 0; mode <= 2; mode++) {
            for (int color = 0; color < 7; color++) {
                CompoundTag legacy = new CompoundTag();
                legacy.putInt("BossBar", mode);
                legacy.putInt("BossColor", color);
                BossBarSettings settings = BossBarSettings.read(legacy);
                check(settings.mode() == mode && settings.rgb() == BossBarSettings.legacyColor(color), "legacy boss setting lost");
                settings.write(legacy);
                legacy.putInt("BossColor", (color + 1) % 7);
                check(settings.equals(BossBarSettings.read(legacy)), "custom RGB did not win over native fallback");
                CompoundTag clone = legacy.copy();
                clone.getCompound(BossBarSettings.KEY).putInt("RGB", 123);
                check(BossBarSettings.read(legacy).equals(settings), "clone shared mutable settings");
            }
        }
        Random random = new Random(406);
        for (int i = 0; i < 10000; i++) {
            int rgb = random.nextInt(0x1000000);
            float[] hsv = BossBarSettings.rgbToHsv(rgb);
            check(BossBarSettings.hsvToRgb(hsv[0], hsv[1], hsv[2]) == rgb, "RGB/HSV round trip: " + rgb);
        }
        check(BossBarSettings.hsvToRgb(0, 1, 1) == 0xFF0000, "red wheel edge");
        check(BossBarSettings.hsvToRgb(0.5f, 0, 1) == 0xFFFFFF, "wheel center");
        check(BossBarSettings.hsvToRgb(-1, 1, 1) == 0xFF0000, "hue wrapping");
        check(BossBarSettings.hsvToRgb(0.3f, 1, 0) == 0, "black brightness");
        check(new BossBarSettings(-1, 0xFF123456).equals(new BossBarSettings(0, 0x123456)), "mode/RGB mask");
        System.out.println("BOSS_BAR_REGRESSION=PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
