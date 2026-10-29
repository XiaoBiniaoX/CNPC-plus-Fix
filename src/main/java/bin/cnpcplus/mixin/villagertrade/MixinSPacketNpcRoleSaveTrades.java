package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.packets.server.SPacketNpcRoleSave;
import noppes.npcs.roles.RoleInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.roles.RoleVillagerTrader;
import top.cnpcplus.villagertrade.TradeDataStore;

/** 原生包已校验 NPC_ADVANCED 权限；只写服务端当前编辑角色的交易桶。 */
@Mixin(value = SPacketNpcRoleSave.class, remap = false)
public class MixinSPacketNpcRoleSaveTrades {
    @Redirect(method = "handle()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/roles/RoleInterface;load(Lnet/minecraft/nbt/CompoundTag;)V"), require = 1)
    private void cnpcplus$saveTrades(RoleInterface role, CompoundTag tag) {
        if (tag == null) return;
        if (role instanceof RoleVillagerTrader trader) {
            if (!tag.contains("CNPCPlusVillagerTrades", 10)) return;
            CompoundTag data = tag.getCompound("CNPCPlusVillagerTrades");
            if (data.getList("Trades", 10).size() > 256) return;
            trader.saveTrades(TradeDataStore.parseTrades(role.npc.level().registryAccess(), data));
            return; // 客户端 TradeListId 不得改写服务端权威ID。
        }
        role.load(tag);
    }
}
