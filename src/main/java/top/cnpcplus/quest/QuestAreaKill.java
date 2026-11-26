package top.cnpcplus.quest;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.quests.QuestKill;

/** CNPC type-4 targets plus a normalized coordinate cuboid. */
public final class QuestAreaKill extends QuestKill {
    public int x1, y1, z1, x2, y2, z2;

    public QuestAreaKill() { targets = new AreaTargets(targets); }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        x1 = tag.getInt("CNPCPlusKillX1"); y1 = tag.getInt("CNPCPlusKillY1"); z1 = tag.getInt("CNPCPlusKillZ1");
        x2 = tag.getInt("CNPCPlusKillX2"); y2 = tag.getInt("CNPCPlusKillY2"); z2 = tag.getInt("CNPCPlusKillZ2");
        normalize();
        refreshTargets();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        normalize();
        tag.putInt("CNPCPlusKillX1", x1); tag.putInt("CNPCPlusKillY1", y1); tag.putInt("CNPCPlusKillZ1", z1);
        tag.putInt("CNPCPlusKillX2", x2); tag.putInt("CNPCPlusKillY2", y2); tag.putInt("CNPCPlusKillZ2", z2);
    }

    public void normalize() {
        int v = Math.min(x1, x2); x2 = Math.max(x1, x2); x1 = v;
        v = Math.min(y1, y2); y2 = Math.max(y1, y2); y1 = v;
        v = Math.min(z1, z2); z2 = Math.max(z1, z2); z1 = v;
    }

    public void refreshTargets() { targets = new AreaTargets(new TreeMap<>(targets)); }

    private boolean allowsCurrentVictim() {
        LivingEntity victim = QuestAreaKillContext.current();
        if (victim == null) return true;
        normalize();
        return victim.getBoundingBox().intersects(x1, y1, z1, (double)x2 + 1, (double)y2 + 1, (double)z2 + 1);
    }

    private final class AreaTargets extends TreeMap<String, Integer> {
        private AreaTargets(Map<String, Integer> source) { super(source); }
        @Override public boolean containsKey(Object key) { return allowsCurrentVictim() && super.containsKey(key); }
    }
}
