package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.accessor.EquipmentModelDataAccessor;
import net.minecraft.nbt.NBTTagCompound;
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

    @Unique public ModelPartConfig mainhand = new ModelPartConfig();
    @Unique public ModelPartConfig offhand = new ModelPartConfig();
    @Unique public ModelPartConfig helmet = new ModelPartConfig();
    @Unique public ModelPartConfig chestplate = new ModelPartConfig();
    @Unique public ModelPartConfig leggings = new ModelPartConfig();
    @Unique public ModelPartConfig boots = new ModelPartConfig();

    @Override public ModelPartConfig getMainhand() { return mainhand; }
    @Override public ModelPartConfig getOffhand() { return offhand; }
    @Override public ModelPartConfig getHelmet() { return helmet; }
    @Override public ModelPartConfig getChestplate() { return chestplate; }
    @Override public ModelPartConfig getLeggings() { return leggings; }
    @Override public ModelPartConfig getBoots() { return boots; }

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void saveEquipment(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound tag = cir.getReturnValue();
        if (tag == null) return;
        tag.setTag("MainhandConfig", this.mainhand.writeToNBT());
        tag.setTag("OffhandConfig", this.offhand.writeToNBT());
        tag.setTag("HelmetConfig", this.helmet.writeToNBT());
        tag.setTag("ChestplateConfig", this.chestplate.writeToNBT());
        tag.setTag("LeggingsConfig", this.leggings.writeToNBT());
        tag.setTag("BootsConfig", this.boots.writeToNBT());
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), remap = false)
    private void loadEquipment(NBTTagCompound compound, CallbackInfo ci) {
        if (compound == null) return;
        if (compound.hasKey("MainhandConfig")) this.mainhand.readFromNBT(compound.getCompoundTag("MainhandConfig"));
        if (compound.hasKey("OffhandConfig")) this.offhand.readFromNBT(compound.getCompoundTag("OffhandConfig"));
        if (compound.hasKey("HelmetConfig")) this.helmet.readFromNBT(compound.getCompoundTag("HelmetConfig"));
        if (compound.hasKey("ChestplateConfig")) this.chestplate.readFromNBT(compound.getCompoundTag("ChestplateConfig"));
        if (compound.hasKey("LeggingsConfig")) this.leggings.readFromNBT(compound.getCompoundTag("LeggingsConfig"));
        if (compound.hasKey("BootsConfig")) this.boots.readFromNBT(compound.getCompoundTag("BootsConfig"));
    }
}
