package top.cnpcplus.roles;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleInterface;
import org.jetbrains.annotations.Nullable;
import top.cnpcplus.villagertrade.TradeDataStore;

/**
 * 村民交易商身份。type ID = 8。
 * NPC NBT 只存 tradeListId（int），交易数据在 TradeDataStore（独立存储）。
 */
public class RoleVillagerTrader extends RoleInterface {

    public int tradeListId = 0;
    private List<TradeData> cache = null; // 缓冲层

    public RoleVillagerTrader(EntityNPCInterface npc) {
        super(npc);
        // 根因B：nextId 此前零调用 → 所有 NPC tradeListId=0 共享存储桶互相串数据
        // （"新NPC打开编辑页显示之前的编辑"）。仅服务端 + 存储就绪时分配：
        // 世界加载期 ServerStartedEvent 还没跑（storageFile=null）→ 不分配，
        // 随后 load() 用 NBT 里的 TradeListId 覆写；客户端 id 由服务端同步而来。
        if (npc.level() != null && !npc.level().isClientSide && TradeDataStore.isReady()) {
            this.tradeListId = TradeDataStore.nextId();
        }
    }

    /** 获取交易列表（带缓存）。 */
    public List<TradeData> getTrades() {
        if (this.npc.level().isClientSide) {
            if (cache == null) {
                cache = TradeDataStore.loadClient(tradeListId);
            }
            return cache;
        }
        // 服务端直读存储（缓冲层只在客户端）：编辑面板实时 saveTrades 后，
        // 玩家交易页/交互必须立刻拿到新数据，不能命中旧缓存（Fix C）。
        return TradeDataStore.load(tradeListId);
    }

    /** 保存交易列表到存储并刷新缓存。 */
    public void saveTrades(List<TradeData> trades) {
        this.cache = trades;
        TradeDataStore.save(tradeListId, trades);
    }

    /** 由交易列表构建原版 MerchantOffers。 */
    public MerchantOffers buildOffers() {
        MerchantOffers offers = new MerchantOffers();
        List<TradeData> trades = getTrades();
        boolean dirty = false;
        for (int i = 0; i < trades.size(); i++) {
            TradeData t = trades.get(i);
            if (t.output.isEmpty()) continue;
            // 限购：limitCount 接进 vanilla maxUses（uses>=maxUses → isOutOfStock →
            // 容器跳过 + 客户端红X + 包同步，整条耗尽链现成）。非限购 = 原版 maxUses 语义。
            int maxU = t.limitEnabled ? Math.max(1, t.limitCount) : t.maxUses;
            int uses = t.uses;
            if (t.limitEnabled && uses >= maxU && t.refreshInterval > 0) {
                if (t.limitResetAt <= 0) {
                    // 阶段224根因：耗尽后才配置刷新秒时计时从未启动（resetAt 恒0 →
                    // 重置条件永不满足 → "重新进玩家页限购未重置"）。开页时启动计时；
                    // 编辑页设置刷新时也会立即启动（maybeArmRefresh），双路幂等。
                    t.limitResetAt = System.currentTimeMillis() + (long) (t.refreshInterval * 1000.0);
                    dirty = true;
                } else if (System.currentTimeMillis() >= t.limitResetAt) {
                    // 刷新窗口已过：uses/limitResetAt 一起回写并清零——否则旧 resetAt 残留，
                    // 下次耗尽 onTradeConsumed 重新计时被旧值顶掉（限购永久失效的第二根因）。
                    uses = 0;
                    t.uses = 0;
                    t.limitResetAt = 0;
                    dirty = true;
                }
            }
            // 用 NBT 构造器恢复 uses（MerchantOffer 无 setUses，官方构造器直读 uses 键）
            CompoundTag tag = new CompoundTag();
            tag.put("buy", t.inputA.save(new CompoundTag()));
            tag.put("buyB", t.inputB.save(new CompoundTag()));
            tag.put("sell", t.output.save(new CompoundTag()));
            tag.putInt("uses", uses);
            tag.putInt("maxUses", maxU);
            tag.putInt("xp", t.xp);
            tag.putFloat("priceMultiplier", t.priceMult);
            MerchantOffer offer = new MerchantOffer(tag);
            ((top.cnpcplus.villagertrade.NameOnlyOffer) (Object) offer).cnpcplus$setNameOnly(t.nameOnly);
            offers.add(offer);
        }
        if (dirty) {
            TradeDataStore.save(tradeListId, trades);
        }
        return offers;
    }

    /**
     * 交易完成写回（服务端）。uses++ 已由 SimpleMerchant.notifyTrade 完成（照抄
     * AbstractVillager.notifyTrade 首行 offer.use()），这里只把次数落进存储：
     * 跨开页持久 + 耗尽时启动刷新计时。仅限购条目写回——非限购保持原版"重开刷新"行为。
     *
     * @param nonEmptyIdx merchant offers 里非空条目序号（= buildOffers 跳空 output 后的下标）
     */
    public void onTradeConsumed(int nonEmptyIdx, MerchantOffer offer) {
        if (this.npc == null || this.npc.level().isClientSide) {
            return;
        }
        List<TradeData> trades = getTrades();
        int seen = -1;
        for (int i = 0; i < trades.size(); i++) {
            if (trades.get(i).output.isEmpty()) continue;
            seen++;
            if (seen != nonEmptyIdx) continue;
            TradeData t = trades.get(i);
            if (!t.limitEnabled) return; // 非限购：不持久，重开满血（原版语义）
            t.uses = offer.getUses();
            int maxU = Math.max(1, t.limitCount);
            if (t.uses >= maxU && t.refreshInterval > 0) {
                // 每次耗尽都重新计时。阶段224：原 ==0 门槛在 limitResetAt 残留旧值时
                // 永不再启动新窗口 → 刷新过期后限购永久失效。
                t.limitResetAt = System.currentTimeMillis() + (long) (t.refreshInterval * 1000.0);
            } else if (t.uses < maxU) {
                t.limitResetAt = 0;
            }
            TradeDataStore.save(tradeListId, trades);
            return;
        }
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        // NPC NBT 只存 ID，不存 ItemStack
        compound.putInt("TradeListId", tradeListId);
        return compound;
    }

    @Override
    public void load(CompoundTag compound) {
        if (compound.contains("TradeListId")) {
            tradeListId = compound.getInt("TradeListId");
            TradeDataStore.reserve(tradeListId); // 已占用，防 nextId 再分配
            cache = null; // 失效缓存
        }
    }

    @Override
    public void interact(Player player) {
        if (this.npc == null || this.npc.level().isClientSide) {
            return;
        }
        // 打开原版村民交易界面。空列表 = 原版直接跳过渲染（isEmpty 守卫），不造假交易
        // （哈基彬反馈：默认 1绿宝石换4面包 的假条目要清掉）。
        MerchantOffers offers = buildOffers();
        SimpleMerchant merchant = new SimpleMerchant(this, offers);
        merchant.setTradingPlayer(player);
        // 根因（阶段220）：原版开交易页 = Merchant.openTradingScreen(player, name, level)，
        // 它在 openMenu 之后还会发 ClientboundMerchantOffersPacket 把 offers 同步到客户端
        // （ServerPlayer.sendMerchantOffers，仅 !isEmpty 时发）。此前直调 sp.openMenu 漏发此包
        // → 客户端 MerchantMenu 永远拿到空列表 → 玩家页从上线起就没有任何条目。
        merchant.openTradingScreen(player, this.npc.getName(), 0);
    }

    @Override
    public int getType() {
        return 8;
    }

    /** 最简 Merchant 实现。 */
    private static class SimpleMerchant implements Merchant {
        private final MerchantOffers offers;
        private final RoleVillagerTrader owner;
        @Nullable
        private Player tradingPlayer;

        SimpleMerchant(RoleVillagerTrader owner, MerchantOffers offers) {
            this.owner = owner;
            this.offers = offers;
        }

        @Override
        public void setTradingPlayer(@Nullable Player player) {
            this.tradingPlayer = player;
        }

        @Nullable
        @Override
        public Player getTradingPlayer() {
            return this.tradingPlayer;
        }

        @Override
        public MerchantOffers getOffers() {
            return this.offers;
        }

        @Override
        public void overrideOffers(MerchantOffers offers) {
        }

        @Override
        public void notifyTrade(MerchantOffer offer) {
            // 根因（阶段222）：vanilla AbstractVillager.notifyTrade 首行就是 offer.increaseUses()
            // （uses++ → isOutOfStock 生效）。此前这里是空的 → uses 恒 0 → 限购/16次上限
            // 全部失效 = "想买多少买多少"。
            offer.increaseUses();
            int nonEmptyIdx = this.offers.indexOf(offer);
            if (nonEmptyIdx >= 0) {
                owner.onTradeConsumed(nonEmptyIdx, offer);
            }
            // 音效：哈基彬 225 反馈不要村民交易音效 → 原版 playTradeSound 已被
            // MixinMerchantMenuTradeSound 取消（防 (Entity) 强转 CCE），此处也不补播 = 全程静音；
            // 原版 Entity 商人（村民/流浪商人）不受影响，照常有声。
        }

        @Override
        public void notifyTradeUpdated(ItemStack stack) {
        }

        @Override
        public int getVillagerXp() {
            return 0;
        }

        @Override
        public void overrideXp(int xp) {
        }

        @Override
        public boolean showProgressBar() {
            return false;
        }

        @Override
        public SoundEvent getNotifyTradeSound() {
            return SoundEvents.VILLAGER_YES;
        }

        @Override
        public boolean isClientSide() {
            return false;
        }
    }
}
