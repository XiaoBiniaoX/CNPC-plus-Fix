package bin.cnpcplus.mixin.persist;

import bin.cnpcplus.data.NpcExternalData;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 读档在 {@code readEntityFromNBT} 入口贴回内联数据；存档在 {@code writeEntityToNBT}
 * 出口摘除。1.20.1 对应 {@code m_7378_}/{@code m_7380_}，这样区块存档以外的克隆、
 * 灵魂石、NBT 读取器也不会再看到 NpcInv/Scripts/TraderMarket/SpawnerNBT*。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcExternalData {
    @Inject(method = "func_70037_a", at = @At("HEAD"), require = 1)
    private void cnpcplus$restoreExternalData(NBTTagCompound tag, CallbackInfo ci) {
        NpcExternalData.load((EntityNPCInterface) (Object) this, tag);
    }

    @Inject(method = "func_70014_b", at = @At("RETURN"), require = 1)
    private void cnpcplus$stripExternalData(NBTTagCompound tag, CallbackInfo ci) {
        NpcExternalData.saveTree((EntityNPCInterface) (Object) this, tag);
    }
}
