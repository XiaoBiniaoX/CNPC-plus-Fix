package top.cnpcplus.mixin;

import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.server.SPacketMenuSave;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * S3（High）：SPacketMenuSave.handle 无条件置 updateClient=true。
 * 改为仅影响客户端可见状态的菜单类型才置位，降低无谓广播（并缩小 S1 放大面）。
 * TRANSFORM/MOVING_PATH/MARK 不置位：TRANSFORM 有效性变化已走 updateAI；
 * MOVING_PATH 是服务端寻路；MARK 自带 syncClients。
 */
@Mixin(value = SPacketMenuSave.class, remap = false)
public abstract class MixinSPacketMenuSaveTypeUpdate {

    @Shadow(remap = false)
    private EnumMenuType type;

    @Redirect(method = "handle", at = @At(value = "FIELD",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;updateClient:Z",
            opcode = Opcodes.PUTFIELD), remap = false)
    private void cnpcplus$conditionalUpdateClient(EntityNPCInterface npc, boolean value) {
        if (type == EnumMenuType.DISPLAY || type == EnumMenuType.STATS
                || type == EnumMenuType.INVENTORY || type == EnumMenuType.AI
                || type == EnumMenuType.ADVANCED || type == EnumMenuType.MODEL) {
            npc.updateClient = true;
        }
    }
}
