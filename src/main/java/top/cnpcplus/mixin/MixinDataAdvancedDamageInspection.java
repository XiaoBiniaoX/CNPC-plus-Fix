package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.DamageInspectionAccess;
import noppes.npcs.entity.EntityNPCInterface;

@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedDamageInspection implements DamageInspectionAccess {
    @Shadow(remap = false) private EntityNPCInterface npc;
    @Unique private boolean cnpcplus$damageInspection;
    @Unique private int cnpcplus$inspectionDistance = 3;

    @Override
    public boolean cnpcplus$isDamageInspectionEnabled() {
        return this.cnpcplus$damageInspection;
    }

    @Override
    public void cnpcplus$setDamageInspectionEnabled(boolean enabled) {
        this.cnpcplus$damageInspection = enabled;
    }

    @Override
    public int cnpcplus$getInspectionDistance() { return this.cnpcplus$inspectionDistance; }

    @Override
    public void cnpcplus$setInspectionDistance(int distance) {
        this.cnpcplus$inspectionDistance = Math.max(1, Math.min(16, distance));
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveInspection(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        tag.putBoolean("CNPCPlusDamageInspection", this.cnpcplus$damageInspection);
        tag.putInt("CNPCPlusInspectionDistance", this.cnpcplus$inspectionDistance);
    }

    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$loadInspection(CompoundTag tag, CallbackInfo ci) {
        this.cnpcplus$damageInspection = tag.getBoolean("CNPCPlusDamageInspection");
        this.cnpcplus$inspectionDistance = tag.contains("CNPCPlusInspectionDistance")
            ? Math.max(1, Math.min(16, tag.getInt("CNPCPlusInspectionDistance"))) : 3;
    }
}
