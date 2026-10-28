package bin.cnpcplus.villagertrade;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleInterface;

public final class RoleVillagerTrader extends RoleInterface {
    public int tradeListId;
    public RoleVillagerTrader(EntityNPCInterface npc) { super(npc); }
    public int ensureId() {
        if (!npc.world.isRemote) tradeListId = TradeStore.ensure(tradeListId, npc.getUniqueID());
        return tradeListId;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { tag.setInteger("TradeListId", tradeListId); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { if (tag.hasKey("TradeListId", 99)) tradeListId = Math.max(0, tag.getInteger("TradeListId")); }
    @Override public int getType() { return 8; }
    @Override public void interact(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && !npc.world.isRemote) {
            bin.cnpcplus.smelting.SmeltingGuiOpener.openVillager((EntityPlayerMP) player, npc, false);
        }
    }
}
