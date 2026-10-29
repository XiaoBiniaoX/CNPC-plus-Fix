package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.packets.server.SPacketNpcRoleGet;
import noppes.npcs.roles.RoleInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.roles.RoleVillagerTrader;
import top.cnpcplus.villagertrade.TradeDataStore;

/** 交易列表仅附于编辑响应，实体持久化仍只写 TradeListId。 */
@Mixin(value = SPacketNpcRoleGet.class, remap = false)
public class MixinSPacketNpcRoleGetTrades {
    @Redirect(method = "handle()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/roles/RoleInterface;save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"), require = 1)
    private CompoundTag cnpcplus$sendTrades(RoleInterface role, CompoundTag tag) {
        if (role instanceof RoleVillagerTrader trader) {
            var trades = trader.getTrades(); // 先确保尚未落盘的新角色已分配ID。
            role.save(tag);
            tag.put("CNPCPlusVillagerTrades", TradeDataStore.encodeTrades(role.npc.level().registryAccess(), trades));
            return tag;
        }
        return role.save(tag);
    }
}
