package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.ai.ReturnHomeMoveHelper;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.entity.EntityCreature;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinReturnHomeControls extends EntityCreature {
    protected MixinReturnHomeControls(World world) { super(world); }
    @Inject(method = "updateTasks", at = @At("RETURN"), require = 1)
    private void cnpcplus$installFinalController(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.getMoveHelper().getClass() == EntityMoveHelper.class) {
            moveHelper = new ReturnHomeMoveHelper(npc);
        }
    }
}
