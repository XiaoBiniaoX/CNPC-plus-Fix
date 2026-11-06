package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcplus.bossbar.BossBarData;
import top.cnpcplus.bossbar.BossBarSettings;
import top.cnpcplus.data.NpcHitboxStore;

@Mixin(value = DataDisplay.class, remap = false)
public abstract class MixinDataDisplayScriptExtensions {
    @Shadow EntityNPCInterface npc;
    @Unique public void setHitbox(float length, float width, float height) {
        for (float value : new float[]{length, width, height}) if (!Float.isFinite(value) || value < 0 || value > NpcHitboxStore.MAX)
            throw new IllegalArgumentException("Hitbox dimensions must be 0..100 blocks (0 = native)");
        var old = NpcHitboxStore.get((DataDisplay)(Object)this);
        NpcHitboxStore.put((DataDisplay)(Object)this, new NpcHitboxStore.Settings(length, width, height, old.shadowScale()));
        npc.refreshDimensions();
        npc.updateClient = true;
    }
    @Unique public float getHitboxLength() { return NpcHitboxStore.get((DataDisplay)(Object)this).length(); }
    @Unique public float getHitboxWidth() { return NpcHitboxStore.get((DataDisplay)(Object)this).width(); }
    @Unique public float getHitboxHeight() { return NpcHitboxStore.get((DataDisplay)(Object)this).height(); }
    @Unique public void setHitboxLength(float value) { setHitbox(value, getHitboxWidth(), getHitboxHeight()); }
    @Unique public void setHitboxWidth(float value) { setHitbox(getHitboxLength(), value, getHitboxHeight()); }
    @Unique public void setHitboxHeight(float value) { setHitbox(getHitboxLength(), getHitboxWidth(), value); }
    @Unique public void setShadowScale(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Shadow scale must be 0..1");
        var old = NpcHitboxStore.get((DataDisplay)(Object)this);
        NpcHitboxStore.put((DataDisplay)(Object)this, new NpcHitboxStore.Settings(old.length(), old.width(), old.height(), value));
        npc.updateClient = true;
    }
    @Unique public float getShadowScale() { return NpcHitboxStore.get((DataDisplay)(Object)this).shadowScale(); }
    @Unique public void setBossBarColor(int red, int green, int blue) {
        if ((red | green | blue) < 0 || red > 255 || green > 255 || blue > 255) throw new IllegalArgumentException("RGB channels must be 0..255");
        BossBarData.put(npc, new BossBarSettings(BossBarData.get(npc.display).mode(), red << 16 | green << 8 | blue));
    }
    @Unique public void setBossBarColor(String hex) {
        if (hex == null || !hex.matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Boss color must be RRGGBB");
        BossBarData.put(npc, new BossBarSettings(BossBarData.get(npc.display).mode(), Integer.parseInt(hex.replace("#", ""), 16)));
    }
    @Unique public String getBossBarColor() { return String.format("%06X", BossBarData.get(npc.display).rgb()); }
}
