package bin.cnpcplus.mixin.packets;

import noppes.npcs.packets.PacketServerBasic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 跳过 PacketServerBasic.handle 中的 toolAllowed（手持魔杖）检查。
 *
 * <p>CNPC 原版所有未覆写 toolAllowed 的 SPacket 都要求主手持有 wand，
 * 包括玩家游玩路径（如 SPacketCompanionTalentExp 伙伴天赋加点）。
 * 这些包已有自己的逻辑校验（角色检查/权限检查），wand 检查是多余的反作弊措施，
 * 但会阻止正常玩家操作。
 */
@Mixin(value = PacketServerBasic.class, remap = false)
public class MixinPacketServerBasicTool {

    @Redirect(
        method = "handle(Lnoppes/npcs/packets/PacketServerBasic;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnoppes/npcs/packets/PacketServerBasic;toolAllowed(Lnet/minecraft/world/item/ItemStack;)Z"),
        remap = false
    )
    private static boolean cnpcplus$skipToolCheck(PacketServerBasic instance, net.minecraft.world.item.ItemStack item) {
        return true; // 跳过 wand 检查，各包自有逻辑校验
    }
}
