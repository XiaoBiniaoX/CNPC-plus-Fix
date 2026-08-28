package bin.cnpcplus.mixin.stats;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import bin.cnpcplus.common.IDataStatsFloatAccess;
import bin.cnpcplus.common.IDataMeleeFloatAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCInterfaceStatsSync {

    @Redirect(method = "func_70636_d", at = @At(value = "FIELD",
            target = "Lnoppes/npcs/entity/data/DataStats;healthRegen:I", ordinal = 0), require = 1)
    private int cnpcplus$allowFractionalHealthRegen(noppes.npcs.entity.data.DataStats stats) {
        return ((IDataStatsFloatAccess) stats).cnpcplus$getHealthRegenFloat() > 0.0f ? 1 : 0;
    }

    @Redirect(method = "func_70636_d", at = @At(value = "FIELD",
            target = "Lnoppes/npcs/entity/data/DataStats;combatRegen:I", ordinal = 0), require = 1)
    private int cnpcplus$allowFractionalCombatRegen(noppes.npcs.entity.data.DataStats stats) {
        return ((IDataStatsFloatAccess) stats).cnpcplus$getCombatRegenFloat() > 0.0f ? 1 : 0;
    }

    @ModifyVariable(method = "func_70652_k", at = @At("STORE"), ordinal = 0, require = 1)
    private float cnpcplus$useFloatMeleeDamage(float original) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        return ((IDataMeleeFloatAccess) self.stats.melee).cnpcplus$getStrengthFloat();
    }

    @ModifyArg(method = "func_70636_d", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;func_70691_i(F)V", ordinal = 0),
            index = 0, require = 1)
    private float cnpcplus$useFloatHealthRegen(float original) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        return ((IDataStatsFloatAccess) self.stats).cnpcplus$getHealthRegenFloat();
    }

    @ModifyArg(method = "func_70636_d", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;func_70691_i(F)V", ordinal = 1),
            index = 0, require = 1)
    private float cnpcplus$useFloatCombatRegen(float original) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        return ((IDataStatsFloatAccess) self.stats).cnpcplus$getCombatRegenFloat();
    }

    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/NBTTagCompound;", at = @At("RETURN"))
    private void cnpcplus$onWriteSpawnData(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound compound = cir.getReturnValue();
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        float hp = ((IDataStatsFloatAccess) self.stats).cnpcplus$getMaxHealthFloat();
        compound.setFloat("CNPCPlusMaxHealth", hp);
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("TAIL"))
    private void cnpcplus$onReadSpawnData(NBTTagCompound compound, CallbackInfo ci) {
        if (compound.hasKey("CNPCPlusMaxHealth")) {
            EntityNPCInterface self = (EntityNPCInterface) (Object) this;
            float health = compound.getFloat("CNPCPlusMaxHealth");
            if (!Float.isFinite(health) || health <= 0.0f) {
                health = 20.0f;
            }
            ((IDataStatsFloatAccess) self.stats).cnpcplus$setMaxHealthFloat(health);
            self.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue((double) health);
            self.setHealth(self.getMaxHealth());
        }
    }
}
