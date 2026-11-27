package bin.cnpcplus.quest;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.NBTTags;
import noppes.npcs.quests.QuestKill;

public final class QuestAreaKill extends QuestKill {
    public int x1, y1, z1, x2, y2, z2;
    public boolean boundsSet;

    public QuestAreaKill() { wrapTargets(); }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        x1 = tag.getInteger("CNPCPlusKillX1"); y1 = tag.getInteger("CNPCPlusKillY1"); z1 = tag.getInteger("CNPCPlusKillZ1");
        x2 = tag.getInteger("CNPCPlusKillX2"); y2 = tag.getInteger("CNPCPlusKillY2"); z2 = tag.getInteger("CNPCPlusKillZ2");
        boundsSet = tag.getBoolean("CNPCPlusKillBoundsSet");
        normalize();
        wrapTargets();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        normalize();
        tag.setInteger("CNPCPlusKillX1", x1); tag.setInteger("CNPCPlusKillY1", y1); tag.setInteger("CNPCPlusKillZ1", z1);
        tag.setInteger("CNPCPlusKillX2", x2); tag.setInteger("CNPCPlusKillY2", y2); tag.setInteger("CNPCPlusKillZ2", z2);
        tag.setBoolean("CNPCPlusKillBoundsSet", boundsSet);
    }

    public void normalize() {
        int v = Math.min(x1, x2); x2 = Math.max(x1, x2); x1 = v;
        v = Math.min(y1, y2); y2 = Math.max(y1, y2); y1 = v;
        v = Math.min(z1, z2); z2 = Math.max(z1, z2); z1 = v;
    }

    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = x1; this.y1 = y1; this.z1 = z1; this.x2 = x2; this.y2 = y2; this.z2 = z2;
        boundsSet = true;
        normalize();
    }

    private void wrapTargets() { targets = new AreaTargets(new TreeMap<String, Integer>(targets)); }

    private boolean allowsCurrentVictim() {
        EntityLivingBase victim = QuestAreaKillContext.current();
        if (victim == null) return true;
        if (!boundsSet) return false;
        normalize();
        return victim.getEntityBoundingBox().intersects(x1, y1, z1, (double)x2 + 1, (double)y2 + 1, (double)z2 + 1);
    }

    private final class AreaTargets extends TreeMap<String, Integer> {
        private AreaTargets(Map<String, Integer> source) { super(source); }
        @Override public boolean containsKey(Object key) { return allowsCurrentVictim() && super.containsKey(key); }
    }
}
