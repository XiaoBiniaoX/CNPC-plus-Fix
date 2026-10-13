package top.cnpcplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.client.PacketNpcUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.sync.client.PendingNpcUpdateStore;

/**
 * S1（Critical）：实体未注册时不再静默丢包，缓存待实体出现后补放。
 * 原版 handle()：entity == null 直接丢弃，无重试——区块加载晚于同步包时
 * 「刚关的页面又回弹 / 加载区块后 NPC 无法交互」的总放大器。
 */
@Mixin(value = PacketNpcUpdate.class, remap = false)
public abstract class MixinPacketNpcUpdateRetry {

    @Shadow(remap = false)
    private int id;

    @Shadow(remap = false)
    private CompoundTag data;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$cacheIfEntityMissing(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity entity = level.getEntity(this.id);
        if (!(entity instanceof EntityNPCInterface)) {
            PendingNpcUpdateStore.store(this.id, this.data);
            ci.cancel();
        }
    }
}
