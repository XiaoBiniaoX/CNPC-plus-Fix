package bin.cnpcplus.mixin.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * reset() 只向同维度玩家广播，防止跨维度幽灵实体。
 *
 * <p>CNPC 原版 reset() 遍历全服玩家不按维度过滤，其他维度玩家收到 handleAddEntity
 * 会生成该 NPC 的幽灵副本（点击无响应、无法交互）。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceReset {

    @Redirect(
        method = "reset()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"),
        remap = false
    )
    private List<ServerPlayer> cnpcplus$filterSameDimension(PlayerList playerList) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        List<ServerPlayer> filtered = new ArrayList<>();
        for (ServerPlayer p : playerList.getPlayers()) {
            if (p.level() == self.level()) {
                filtered.add(p);
            }
        }
        return filtered;
    }
}
