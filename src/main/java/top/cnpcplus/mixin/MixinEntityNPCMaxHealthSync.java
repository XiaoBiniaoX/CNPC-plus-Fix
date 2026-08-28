package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCMaxHealthSync {

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void cnpcplus$syncMaxHealthAfterSpawn(CompoundTag compound, CallbackInfo ci) {
        cnpcplus$forceMaxHealth((EntityNPCInterface) (Object) this);
    }

    @Inject(method = "m_7378_(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void cnpcplus$syncMaxHealthAfterLoad(CompoundTag compound, CallbackInfo ci) {
        cnpcplus$forceMaxHealth((EntityNPCInterface) (Object) this);
    }

    private static void cnpcplus$forceMaxHealth(EntityNPCInterface npc) {
        if (npc.stats == null) return;
        var attr = npc.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        float v = ExtraDataStorage.getFloat(npc.stats, 0);
        attr.setBaseValue(v < 0.0f ? (double) npc.stats.maxHealth : (double) v);
    }
}