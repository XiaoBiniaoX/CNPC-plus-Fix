package bin.cnpcplus.mixin.entity;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家开始跟踪 NPC 时强制同步，修复假死后无法交互。
 *
 * <p>假死期间客户端实体被 remove(KILLED)，服务端照常发包但 getEntity(id)=null 全部丢弃。
 * 复活后客户端重新加载实体，但服务端不知道需要重发数据 → 玩家点击无响应。
 * 此处 startSeenByPlayer 时置 updateClient=true，下一 tick 全量重发。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceSeenBy {

    @Inject(method = "startSeenByPlayer", at = @At("TAIL"), remap = false)
    private void cnpcplus$forceResync(net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (!self.level().isClientSide) {
            self.updateClient = true;
        }
    }
}
