package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.common.INpcTasksAccess;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;

/** 暴露 CNPC 的普通 AI 任务列表给视察控制器。 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCTasksAccess extends EntityCreature implements INpcTasksAccess {

    protected MixinEntityNPCTasksAccess(World world) {
        super(world);
    }

    @Override
    public EntityAITasks cnpcplus$getTasks() {
        return this.tasks;
    }
}
