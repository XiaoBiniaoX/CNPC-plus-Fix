package bin.cnpcplus.mixin.client;

import noppes.npcs.controllers.QuestController;
import noppes.npcs.packets.client.PacketQuestCompletion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PacketQuestCompletion.handle 空判，防止任务未同步到客户端时 NPE 崩溃。
 *
 * <p>CNPC 原版直接 quest.getCompleteText() 不判 null，
 * 任务数据未同步到客户端（时序问题）时客户端崩溃。
 */
@Mixin(value = PacketQuestCompletion.class, remap = false)
public class MixinPacketQuestCompletion {

    @Shadow(remap = false)
    private int id;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$nullCheck(CallbackInfo ci) {
        if (QuestController.instance.get(this.id) == null) {
            ci.cancel();
        }
    }
}
