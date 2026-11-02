package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.data.NpcHitboxStore;
import net.minecraft.entity.EntityCreature;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcRectangularBox extends EntityCreature {
    protected MixinNpcRectangularBox(World world) { super(world); }
    @Override public void setEntityBoundingBox(AxisAlignedBB box) {
        super.setEntityBoundingBox(NpcHitboxStore.apply((EntityNPCInterface) (Object) this, box));
    }
}
