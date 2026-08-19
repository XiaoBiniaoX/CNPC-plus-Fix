package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A6: 高血量（>=1000）NPC 脱离加载区块后血量显示 20。
 * 客户端实体重新生成时 spawnData 的 MaxHealth 只更新 stats.maxHealth 字段与属性，
 * 但属性 MAX_HEALTH 的 baseValue 可能在重新加载路径上丢失（默认 20）。
 * 在两个读取入口 RETURN 后强制把属性设为 stats.maxHealth，保证客户端血量条/接口读取正确。
 */
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
        attr.setBaseValue(npc.stats.getMaxHealth());
    }
}
