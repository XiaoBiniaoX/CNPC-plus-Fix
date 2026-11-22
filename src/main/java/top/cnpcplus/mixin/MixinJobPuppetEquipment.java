package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.puppet.JobPuppetAccessor;

@Mixin(value = JobPuppet.class, remap = false)
public abstract class MixinJobPuppetEquipment implements JobPuppetAccessor {
    @Shadow public int animationSpeed;
    @Shadow public boolean animate;
    @Shadow private int startTick;
    @Unique private static final int[] CNPCPLUS_LEGACY_SPEEDS = {1, 2, 3, 4, 6, 10, 13, 20};
    @Unique private static final String CNPCPLUS_SPEED_FORMAT = "CNPCPlusPuppetSpeed20";

    @ModifyConstant(method = "setAnimationSpeed", constant = @Constant(intValue = 7))
    private int cnpcplus$maxAnimationSpeed(int oldMax) { return 20; }

    @Inject(method = "calcRotation", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$interpolate(float start, float end, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!animate || animationSpeed == 0) {
            cir.setReturnValue(start);
            return;
        }
        int ticks = ((JobPuppet) (Object) this).npc.tickCount - startTick;
        float phase = ((ticks % 160) + partialTick) * animationSpeed * Mth.PI / 80;
        cir.setReturnValue(Mth.lerp((1 - Mth.cos(phase)) / 2, start, end));
    }
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

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cnpcplus$initEquipmentParts(EntityNPCInterface npc, CallbackInfo ci) {
        animationSpeed = CNPCPLUS_LEGACY_SPEEDS[animationSpeed];
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

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveEquipmentParts(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putBoolean(CNPCPLUS_SPEED_FORMAT, true);
        tag.put("PuppetMainHand", this.cnpcplus$mainhand.writeNBT());
        tag.put("PuppetOffHand", this.cnpcplus$offhand.writeNBT());
        tag.put("PuppetHelmet", this.cnpcplus$helmet.writeNBT());
        tag.put("PuppetChestplate", this.cnpcplus$chestplate.writeNBT());
        tag.put("PuppetLeggings", this.cnpcplus$leggings.writeNBT());
        tag.put("PuppetBoots", this.cnpcplus$boots.writeNBT());
        tag.put("PuppetMainHand2", this.cnpcplus$mainhand2.writeNBT());
        tag.put("PuppetOffHand2", this.cnpcplus$offhand2.writeNBT());
        tag.put("PuppetHelmet2", this.cnpcplus$helmet2.writeNBT());
        tag.put("PuppetChestplate2", this.cnpcplus$chestplate2.writeNBT());
        tag.put("PuppetLeggings2", this.cnpcplus$leggings2.writeNBT());
        tag.put("PuppetBoots2", this.cnpcplus$boots2.writeNBT());
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void cnpcplus$loadEquipmentParts(CompoundTag compound, CallbackInfo ci) {
        if (!compound.getBoolean(CNPCPLUS_SPEED_FORMAT)) {
            animationSpeed = CNPCPLUS_LEGACY_SPEEDS[Mth.clamp(animationSpeed, 0, 7)];
        }
        this.cnpcplus$mainhand.readNBT(compound.getCompound("PuppetMainHand"));
        this.cnpcplus$offhand.readNBT(compound.getCompound("PuppetOffHand"));
        this.cnpcplus$helmet.readNBT(compound.getCompound("PuppetHelmet"));
        this.cnpcplus$chestplate.readNBT(compound.getCompound("PuppetChestplate"));
        this.cnpcplus$leggings.readNBT(compound.getCompound("PuppetLeggings"));
        this.cnpcplus$boots.readNBT(compound.getCompound("PuppetBoots"));
        this.cnpcplus$mainhand2.readNBT(compound.getCompound("PuppetMainHand2"));
        this.cnpcplus$offhand2.readNBT(compound.getCompound("PuppetOffHand2"));
        this.cnpcplus$helmet2.readNBT(compound.getCompound("PuppetHelmet2"));
        this.cnpcplus$chestplate2.readNBT(compound.getCompound("PuppetChestplate2"));
        this.cnpcplus$leggings2.readNBT(compound.getCompound("PuppetLeggings2"));
        this.cnpcplus$boots2.readNBT(compound.getCompound("PuppetBoots2"));
    }

    @Inject(method = "getPart", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$getEquipmentPart(int part, CallbackInfoReturnable<JobPuppet.PartConfig> cir) {
        switch (part) {
            case 12 -> cir.setReturnValue(this.cnpcplus$mainhand);
            case 13 -> cir.setReturnValue(this.cnpcplus$offhand);
            case 14 -> cir.setReturnValue(this.cnpcplus$helmet);
            case 15 -> cir.setReturnValue(this.cnpcplus$chestplate);
            case 16 -> cir.setReturnValue(this.cnpcplus$leggings);
            case 17 -> cir.setReturnValue(this.cnpcplus$boots);
            case 18 -> cir.setReturnValue(this.cnpcplus$mainhand2);
            case 19 -> cir.setReturnValue(this.cnpcplus$offhand2);
            case 20 -> cir.setReturnValue(this.cnpcplus$helmet2);
            case 21 -> cir.setReturnValue(this.cnpcplus$chestplate2);
            case 22 -> cir.setReturnValue(this.cnpcplus$leggings2);
            case 23 -> cir.setReturnValue(this.cnpcplus$boots2);
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
