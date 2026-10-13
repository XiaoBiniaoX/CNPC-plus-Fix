package top.cnpcplus.mixin;

import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketScriptSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S5（High）：脚本保存（type==0，NPC 脚本）后置 updateClient=true。
 * 原版 SPacketScriptSave.handle 只 load+updateAI，从不进 updateClient 广播，
 * 脚本改的 NPC 表现其他玩家看不到。
 */
@Mixin(value = SPacketScriptSave.class, remap = false)
public abstract class MixinSPacketScriptSaveUpdateClient {

    @Shadow(remap = false)
    private int type;

    @Inject(method = "handle", at = @At("RETURN"), remap = false)
    private void cnpcplus$markUpdateClient(CallbackInfo ci) {
        if (this.type != 0) return;
        var npc = ((PacketServerBasic) (Object) this).npc;
        if (npc != null) {
            npc.updateClient = true;
        }
    }
}
