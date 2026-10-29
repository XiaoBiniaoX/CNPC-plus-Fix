package top.cnpcplus.roles;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobInterface;

/**
 * 测试职业。type ID = 12。
 * CNPC 的 setJob 会做 {@code job %= 12;} 篡改，本类只能经由
 * {@code MixinDataAdvancedCustomRoleJob} 在 HEAD 拦截后实例化。
 */
public class JobTestProfession extends JobInterface {

    public String testMessage = "";

    public JobTestProfession(EntityNPCInterface npc) {
        super(npc);
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        compound.putString("JobTestMessage", this.testMessage);
        return compound;
    }

    @Override
    public void load(CompoundTag compound) {
        this.testMessage = compound.getString("JobTestMessage");
    }

    @Override
    public int getType() {
        return 12;
    }
}
