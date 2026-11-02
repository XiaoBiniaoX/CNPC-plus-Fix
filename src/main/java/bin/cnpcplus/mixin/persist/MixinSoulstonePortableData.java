package bin.cnpcplus.mixin.persist;

import bin.cnpcplus.data.NpcExternalData;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.items.ItemSoulstoneEmpty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemSoulstoneEmpty.class, remap = false)
public abstract class MixinSoulstonePortableData {
    @Redirect(method = "store", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;func_184198_c(Lnet/minecraft/nbt/NBTTagCompound;)Z"), require = 1)
    private boolean cnpcplus$portable(EntityLivingBase entity, NBTTagCompound tag) {
        return entity.writeToNBTOptional(tag) && NpcExternalData.makePortable(tag);
    }
}
