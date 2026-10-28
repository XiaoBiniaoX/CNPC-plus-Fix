package bin.cnpcplus.villagertrade;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** 保留 1.20.1 交易数据键；限购计数跨面板、玩家和服务器重启保存。 */
public final class TradeData {
    public ItemStack a = ItemStack.EMPTY, b = ItemStack.EMPTY, output = ItemStack.EMPTY;
    public boolean limit, nameOnly;
    public int limitCount = 1, uses, maxUses = 16, xp = 2;
    public double refreshSeconds;
    public float priceMultiplier = .05F;
    public long resetAt;
    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("A", a.writeToNBT(new NBTTagCompound())); tag.setTag("B", b.writeToNBT(new NBTTagCompound())); tag.setTag("O", output.writeToNBT(new NBTTagCompound()));
        tag.setBoolean("LimitOn", limit); tag.setBoolean("NameOnly", nameOnly);
        tag.setInteger("LimitCount", limitCount); tag.setInteger("Uses", uses); tag.setInteger("MaxUses", maxUses); tag.setInteger("XP", xp);
        tag.setDouble("Refresh", refreshSeconds); tag.setFloat("Mult", priceMultiplier); tag.setLong("LimitResetAt", resetAt);
        return tag;
    }
    public static TradeData fromNBT(NBTTagCompound tag) {
        TradeData trade = new TradeData();
        trade.a = stack(tag, "A"); trade.b = stack(tag, "B"); trade.output = stack(tag, "O");
        trade.limit = tag.getBoolean("LimitOn"); trade.nameOnly = tag.getBoolean("NameOnly");
        trade.limitCount = Math.max(1, Math.min(10000, tag.getInteger("LimitCount")));
        trade.uses = Math.max(0, tag.getInteger("Uses"));
        trade.maxUses = tag.hasKey("MaxUses", 99) ? Math.max(1, tag.getInteger("MaxUses")) : 16;
        trade.xp = Math.max(0, tag.getInteger("XP"));
        trade.refreshSeconds = tag.getDouble("Refresh");
        if (!Double.isFinite(trade.refreshSeconds)) trade.refreshSeconds = 0;
        trade.refreshSeconds = Math.max(0, Math.min(99999, trade.refreshSeconds));
        trade.priceMultiplier = tag.getFloat("Mult");
        trade.resetAt = Math.max(0, tag.getLong("LimitResetAt"));
        return trade;
    }
    private static ItemStack stack(NBTTagCompound tag, String key) {
        ItemStack stack = new ItemStack(tag.getCompoundTag(key));
        if (!stack.isEmpty()) stack.setCount(Math.min(64, stack.getCount()));
        return stack;
    }
    public TradeData copy() { return fromNBT(toNBT()); }
    public boolean refresh(long now) {
        if (!limit || uses < limitCount || refreshSeconds <= 0) return false;
        if (resetAt == 0) { resetAt = now + (long) (refreshSeconds * 1000); return true; }
        if (now < resetAt) return false;
        uses = 0; resetAt = 0; return true;
    }
}
