package bin.cnpcplus.mixin.penetration;

import bin.cnpcplus.common.IRangedPenetration;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataRanged.class, remap = false)
public class MixinDataRangedPenetration implements IRangedPenetration {
    private static final int MAX_PENETRATION = 16;

    @Unique
    private int cnpcplus$penetration;

    // MC classes are MCP-named at compile time; reobf maps them to SRG names.
    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void cnpcplus$readPenetration(NBTTagCompound compound, CallbackInfo ci) {
        cnpcplus$penetration = compound.hasKey("ProjectilePenetration")
                ? clamp(compound.getInteger("ProjectilePenetration")) : 0;
    }

    // writeToNBT returns NBTTagCompound, so the handler must use CallbackInfoReturnable.
    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writePenetration(NBTTagCompound compound,
                                           CallbackInfoReturnable<NBTTagCompound> cir) {
        compound.setInteger("ProjectilePenetration", cnpcplus$penetration);
    }

    @Override
    public int cnpcplus$getPenetration() {
        return cnpcplus$penetration;
    }

    @Override
    public void cnpcplus$setPenetration(int value) {
        cnpcplus$penetration = clamp(value);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_PENETRATION, value));
    }
}
