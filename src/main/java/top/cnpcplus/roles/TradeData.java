package top.cnpcplus.roles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import java.util.Optional;

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

    public CompoundTag save(HolderLookup.Provider registries, CompoundTag tag) {
        tag.put("A", inputA.saveOptional(registries));
        tag.put("B", inputB.saveOptional(registries));
        tag.put("O", output.saveOptional(registries));
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

    public static TradeData load(HolderLookup.Provider registries, CompoundTag tag) {
        TradeData d = new TradeData();
        d.inputA = ItemStack.parseOptional(registries, tag.getCompound("A"));
        d.inputB = ItemStack.parseOptional(registries, tag.getCompound("B"));
        d.output = ItemStack.parseOptional(registries, tag.getCompound("O"));
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

    public static ItemCost cost(ItemStack stack) {
        // 空兑换槽只用于本地编辑/免费交易，不交给非空 ItemCost 磁盘 codec。
        return new ItemCost(stack.getItemHolder(), stack.getCount(),
                DataComponentPredicate.allOf(stack.getComponents()), stack.copy());
    }

    public MerchantOffer toOffer() {
        return toOffer(0, maxUses);
    }

    public MerchantOffer toOffer(int uses, int maxUses) {
        MerchantOffer offer = new MerchantOffer(cost(inputA),
                inputB.isEmpty() ? Optional.empty() : Optional.of(cost(inputB)),
                output.copy(), uses, maxUses, xp, priceMult);
        ((top.cnpcplus.villagertrade.NameOnlyOffer) offer).cnpcplus$setNameOnly(nameOnly);
        return offer;
    }

    public String displayName() {
        String a = inputA.isEmpty() ? "?" : inputA.getCount() + "x" + inputA.getItem().toString();
        String b = inputB.isEmpty() ? "" : " + " + inputB.getCount() + "x" + inputB.getItem().toString();
        String o = output.isEmpty() ? "?" : output.getCount() + "x" + output.getItem().toString();
        return a + b + " → " + o;
    }
}
