package top.cnpcplus.roles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 单条交易数据 + 限购设置。
 */
public class TradeData {
    public ItemStack inputA = ItemStack.EMPTY;
    public ItemStack inputB = ItemStack.EMPTY;
    public ItemStack output = ItemStack.EMPTY;
    public int maxUses = 16;
    public int xp = 2;
    public float priceMult = 0.05f;
    // 限购
    public boolean limitEnabled = false;
    public int limitCount = 1;
    public double refreshInterval = 0.0; // 秒，0=不刷新
    public int uses = 0;                 // 限购已购次数（跨开页持久；非限购不写）
    public long limitResetAt = 0;         // 耗尽后重置时刻(epoch ms)，0=未耗尽
    // 仅名字检查：NBT 每次都变的 mod 物品专用，匹配只比物品 id + 展示名
    public boolean nameOnly = false;

    public TradeData() {}

    public TradeData(ItemStack a, ItemStack b, ItemStack out, int maxUses, int xp, float mult) {
        this.inputA = a.copy();
        if (b != null) this.inputB = b.copy();
        this.output = out.copy();
        this.maxUses = maxUses;
        this.xp = xp;
        this.priceMult = mult;
    }

    public TradeData(ItemStack a, ItemStack b, ItemStack out, int maxUses, int xp, float mult,
                     boolean limitOn, int limitCount, double refreshInterval) {
        this.inputA = a.copy();
        if (b != null) this.inputB = b.copy();
        this.output = out.copy();
        this.maxUses = maxUses;
        this.xp = xp;
        this.priceMult = mult;
        this.limitEnabled = limitOn;
        this.limitCount = limitCount;
        this.refreshInterval = refreshInterval;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.put("A", inputA.save(new CompoundTag()));
        tag.put("B", inputB.save(new CompoundTag()));
        tag.put("O", output.save(new CompoundTag()));
        tag.putInt("MaxUses", maxUses);
        tag.putInt("XP", xp);
        tag.putFloat("Mult", priceMult);
        tag.putBoolean("LimitOn", limitEnabled);
        tag.putInt("LimitCount", limitCount);
        tag.putDouble("Refresh", refreshInterval);
        tag.putInt("Uses", uses);
        tag.putLong("LimitResetAt", limitResetAt);
        tag.putBoolean("NameOnly", nameOnly);
        return tag;
    }

    public static TradeData load(CompoundTag tag) {
        TradeData d = new TradeData();
        d.inputA = ItemStack.of(tag.getCompound("A"));
        d.inputB = ItemStack.of(tag.getCompound("B"));
        d.output = ItemStack.of(tag.getCompound("O"));
        d.maxUses = tag.getInt("MaxUses");
        d.xp = tag.getInt("XP");
        d.priceMult = tag.getFloat("Mult");
        d.limitEnabled = tag.getBoolean("LimitOn");
        d.limitCount = tag.getInt("LimitCount");
        d.refreshInterval = tag.getDouble("Refresh");
        d.uses = tag.getInt("Uses");
        d.limitResetAt = tag.getLong("LimitResetAt");
        d.nameOnly = tag.getBoolean("NameOnly");
        return d;
    }

    public String displayName() {
        String a = inputA.isEmpty() ? "?" : inputA.getCount() + "x" + inputA.getItem().toString();
        String b = inputB.isEmpty() ? "" : " + " + inputB.getCount() + "x" + inputB.getItem().toString();
        String o = output.isEmpty() ? "?" : output.getCount() + "x" + output.getItem().toString();
        return a + b + " → " + o;
    }
}
