package bin.cnpcplus.bossbar;

import net.minecraft.nbt.NBTTagCompound;

public final class BossBarSettings {
    public static final String KEY = "CNPCPlusBossbar";
    private static final int[] LEGACY_COLORS = {0xFF55FF, 0x5555FF, 0xFF5555, 0x55FF55, 0xFFFF55, 0xAA00AA, 0xFFFFFF};
    public final int mode, rgb;
    public BossBarSettings(int mode, int rgb) { this.mode = Math.max(0, Math.min(2, mode)); this.rgb = rgb & 0xFFFFFF; }
    public static int legacyColor(int index) { return LEGACY_COLORS[Math.max(0, Math.min(6, index))]; }
    public static BossBarSettings read(NBTTagCompound tag) {
        if (!tag.hasKey(KEY, 10)) return new BossBarSettings(tag.getInteger("BossBar"), legacyColor(tag.getInteger("BossColor")));
        NBTTagCompound settings = tag.getCompoundTag(KEY);
        return new BossBarSettings(settings.getInteger("Mode"), settings.getInteger("RGB"));
    }
    public void write(NBTTagCompound tag) {
        NBTTagCompound settings = new NBTTagCompound();
        settings.setInteger("Mode", mode); settings.setInteger("RGB", rgb); tag.setTag(KEY, settings);
    }
}
