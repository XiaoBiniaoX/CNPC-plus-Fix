package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import top.cnpcplus.accessor.EquipmentModelDataAccessor;
import noppes.npcs.ModelDataShared;
import noppes.npcs.ModelPartConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModelDataShared.class, remap = false)
public abstract class MixinModelDataSharedEquipment implements EquipmentModelDataAccessor {

    @Unique
    public ModelPartConfig mainhand = new ModelPartConfig();
    @Unique
    public ModelPartConfig offhand = new ModelPartConfig();
    @Unique
    public ModelPartConfig helmet = new ModelPartConfig();
    @Unique
    public ModelPartConfig chestplate = new ModelPartConfig();
    @Unique
    public ModelPartConfig leggings = new ModelPartConfig();
    @Unique
    public ModelPartConfig boots = new ModelPartConfig();

    @Unique
    public ModelPartConfig getMainhand() { return mainhand; }
    @Unique
    public ModelPartConfig getOffhand() { return offhand; }
    @Unique
    public ModelPartConfig getHelmet() { return helmet; }
    @Unique
    public ModelPartConfig getChestplate() { return chestplate; }
    @Unique
    public ModelPartConfig getLeggings() { return leggings; }
    @Unique
    public ModelPartConfig getBoots() { return boots; }

    @Inject(method = "save", at = @At("RETURN"))
    private void saveEquipment(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.put("MainhandConfig", this.mainhand.writeToNBT());
        tag.put("OffhandConfig", this.offhand.writeToNBT());
        tag.put("HelmetConfig", this.helmet.writeToNBT());
        tag.put("ChestplateConfig", this.chestplate.writeToNBT());
        tag.put("LeggingsConfig", this.leggings.writeToNBT());
        tag.put("BootsConfig", this.boots.writeToNBT());
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void loadEquipment(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("MainhandConfig", 10)) this.mainhand.readFromNBT(compound.getCompound("MainhandConfig"));
        if (compound.contains("OffhandConfig", 10)) this.offhand.readFromNBT(compound.getCompound("OffhandConfig"));
        if (compound.contains("HelmetConfig", 10)) this.helmet.readFromNBT(compound.getCompound("HelmetConfig"));
        if (compound.contains("ChestplateConfig", 10)) this.chestplate.readFromNBT(compound.getCompound("ChestplateConfig"));
        if (compound.contains("LeggingsConfig", 10)) this.leggings.readFromNBT(compound.getCompound("LeggingsConfig"));
        if (compound.contains("BootsConfig", 10)) this.boots.readFromNBT(compound.getCompound("BootsConfig"));
    }
}
