package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.puppet.JobPuppetAccessor;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.entity.data.role.IJobPuppet;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JobPuppet.class, remap = false)
public abstract class MixinJobPuppetEquipment implements JobPuppetAccessor {
    @Unique public JobPuppet.PartConfig cnpcplus$mainhand;
    @Unique public JobPuppet.PartConfig cnpcplus$offhand;
    @Unique public JobPuppet.PartConfig cnpcplus$helmet;
    @Unique public JobPuppet.PartConfig cnpcplus$chestplate;
    @Unique public JobPuppet.PartConfig cnpcplus$leggings;
    @Unique public JobPuppet.PartConfig cnpcplus$boots;
    @Unique public JobPuppet.PartConfig cnpcplus$mainhand2;
    @Unique public JobPuppet.PartConfig cnpcplus$offhand2;
    @Unique public JobPuppet.PartConfig cnpcplus$helmet2;
    @Unique public JobPuppet.PartConfig cnpcplus$chestplate2;
    @Unique public JobPuppet.PartConfig cnpcplus$leggings2;
    @Unique public JobPuppet.PartConfig cnpcplus$boots2;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void cnpcplus$initEquipmentParts(EntityNPCInterface npc, CallbackInfo ci) {
        JobPuppet self = (JobPuppet) (Object) this;
        this.cnpcplus$mainhand = self.new PartConfig();
        this.cnpcplus$offhand = self.new PartConfig();
        this.cnpcplus$helmet = self.new PartConfig();
        this.cnpcplus$chestplate = self.new PartConfig();
        this.cnpcplus$leggings = self.new PartConfig();
        this.cnpcplus$boots = self.new PartConfig();
        this.cnpcplus$mainhand2 = self.new PartConfig();
        this.cnpcplus$offhand2 = self.new PartConfig();
        this.cnpcplus$helmet2 = self.new PartConfig();
        this.cnpcplus$chestplate2 = self.new PartConfig();
        this.cnpcplus$leggings2 = self.new PartConfig();
        this.cnpcplus$boots2 = self.new PartConfig();
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveEquipmentParts(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound tag = cir.getReturnValue();
        if (tag == null) return;
        tag.setTag("PuppetMainHand", this.cnpcplus$mainhand.writeNBT());
        tag.setTag("PuppetOffHand", this.cnpcplus$offhand.writeNBT());
        tag.setTag("PuppetHelmet", this.cnpcplus$helmet.writeNBT());
        tag.setTag("PuppetChestplate", this.cnpcplus$chestplate.writeNBT());
        tag.setTag("PuppetLeggings", this.cnpcplus$leggings.writeNBT());
        tag.setTag("PuppetBoots", this.cnpcplus$boots.writeNBT());
        tag.setTag("PuppetMainHand2", this.cnpcplus$mainhand2.writeNBT());
        tag.setTag("PuppetOffHand2", this.cnpcplus$offhand2.writeNBT());
        tag.setTag("PuppetHelmet2", this.cnpcplus$helmet2.writeNBT());
        tag.setTag("PuppetChestplate2", this.cnpcplus$chestplate2.writeNBT());
        tag.setTag("PuppetLeggings2", this.cnpcplus$leggings2.writeNBT());
        tag.setTag("PuppetBoots2", this.cnpcplus$boots2.writeNBT());
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$loadEquipmentParts(NBTTagCompound compound, CallbackInfo ci) {
        if (compound == null) return;
        this.cnpcplus$mainhand.readNBT(compound.getCompoundTag("PuppetMainHand"));
        this.cnpcplus$offhand.readNBT(compound.getCompoundTag("PuppetOffHand"));
        this.cnpcplus$helmet.readNBT(compound.getCompoundTag("PuppetHelmet"));
        this.cnpcplus$chestplate.readNBT(compound.getCompoundTag("PuppetChestplate"));
        this.cnpcplus$leggings.readNBT(compound.getCompoundTag("PuppetLeggings"));
        this.cnpcplus$boots.readNBT(compound.getCompoundTag("PuppetBoots"));
        this.cnpcplus$mainhand2.readNBT(compound.getCompoundTag("PuppetMainHand2"));
        this.cnpcplus$offhand2.readNBT(compound.getCompoundTag("PuppetOffHand2"));
        this.cnpcplus$helmet2.readNBT(compound.getCompoundTag("PuppetHelmet2"));
        this.cnpcplus$chestplate2.readNBT(compound.getCompoundTag("PuppetChestplate2"));
        this.cnpcplus$leggings2.readNBT(compound.getCompoundTag("PuppetLeggings2"));
        this.cnpcplus$boots2.readNBT(compound.getCompoundTag("PuppetBoots2"));
    }

    @Inject(method = "getPart", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$getEquipmentPart(int part, CallbackInfoReturnable<IJobPuppet.IJobPuppetPart> cir) {
        switch (part) {
            case 12: cir.setReturnValue(this.cnpcplus$mainhand); break;
            case 13: cir.setReturnValue(this.cnpcplus$offhand); break;
            case 14: cir.setReturnValue(this.cnpcplus$helmet); break;
            case 15: cir.setReturnValue(this.cnpcplus$chestplate); break;
            case 16: cir.setReturnValue(this.cnpcplus$leggings); break;
            case 17: cir.setReturnValue(this.cnpcplus$boots); break;
            case 18: cir.setReturnValue(this.cnpcplus$mainhand2); break;
            case 19: cir.setReturnValue(this.cnpcplus$offhand2); break;
            case 20: cir.setReturnValue(this.cnpcplus$helmet2); break;
            case 21: cir.setReturnValue(this.cnpcplus$chestplate2); break;
            case 22: cir.setReturnValue(this.cnpcplus$leggings2); break;
            case 23: cir.setReturnValue(this.cnpcplus$boots2); break;
            default: break;
        }
    }

    @Override public JobPuppet.PartConfig cnpcplus$getMainhand() { return this.cnpcplus$mainhand; }
    @Override public JobPuppet.PartConfig cnpcplus$getOffhand() { return this.cnpcplus$offhand; }
    @Override public JobPuppet.PartConfig cnpcplus$getHelmet() { return this.cnpcplus$helmet; }
    @Override public JobPuppet.PartConfig cnpcplus$getChestplate() { return this.cnpcplus$chestplate; }
    @Override public JobPuppet.PartConfig cnpcplus$getLeggings() { return this.cnpcplus$leggings; }
    @Override public JobPuppet.PartConfig cnpcplus$getBoots() { return this.cnpcplus$boots; }
    @Override public JobPuppet.PartConfig cnpcplus$getMainhand2() { return this.cnpcplus$mainhand2; }
    @Override public JobPuppet.PartConfig cnpcplus$getOffhand2() { return this.cnpcplus$offhand2; }
    @Override public JobPuppet.PartConfig cnpcplus$getHelmet2() { return this.cnpcplus$helmet2; }
    @Override public JobPuppet.PartConfig cnpcplus$getChestplate2() { return this.cnpcplus$chestplate2; }
    @Override public JobPuppet.PartConfig cnpcplus$getLeggings2() { return this.cnpcplus$leggings2; }
    @Override public JobPuppet.PartConfig cnpcplus$getBoots2() { return this.cnpcplus$boots2; }
}
