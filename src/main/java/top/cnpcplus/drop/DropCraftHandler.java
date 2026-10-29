package top.cnpcplus.drop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 掉落物配方生效层（阶段232，服务端权威——客户端不参与合成判定/执行）。
 *
 * <p><b>触发链（无全局扫描，用户性能要求）</b>：
 * <ol>
 *   <li>{@code ItemTossEvent}（玩家丢出物品瞬间，Forge 在 Player.drop 内 fire）：
 *       记 PENDING（id→所在 Level，待落地检查）+ PLAYER_DROPPED（身份集合，
 *       合成输入过滤用——发射器/漏斗/爆炸生成的物品不经过 toss，天然被挡）。
 *       ItemEntity 的 thrower 无公开 getter（javap 实证仅 private 字段+setter），
 *       用事件+身份集合替代读私有字段；<b>边界</b>：重启后 PLAYER_DROPPED 清空，
 *       重启前丢在地上的物品需重新丢出才参与合成（注释明示）。</li>
 *   <li>{@code LevelTickEvent}（END 相，每世界每 tick 一次）：入口 {@code PENDING.isEmpty()}
 *       O(1) 短路——pending 空（绝大多数 tick）直接返回；非空才按维度匹配遍历 pending
 *       查 onGround，命中才做一次方块 AABB 实体查询。1.20.1 Forge 无通用 EntityTickEvent
 *       （javap 实证只有 Living/Item 专用），LevelTickEvent 是唯一免 per-entity 开销的驱动点。</li>
 *   <li>{@code EntityLeaveLevelEvent}：PENDING+PLAYER_DROPPED 双清（被捡走/消亡=零泄漏）。</li>
 * </ol>
 *
 * <p><b>连续合成（用户要求7）</b>：命中后同 tick 内 while 链式——每次只消耗一份（多倍
 * 留接口见 CraftPlan），消耗完再 plan 直到无配方可命中；每次至少消耗 2 个物品（注册条件
 * ≥2 槽有物且每槽 count≥1）→ 原料单调减少必终止，另设 64 次防御上限。
 *
 * <p><b>稳定优先级（用户要求7）</b>：同格输入匹配多条配方 → 按配方 id 升序取第一条
 * （DropRecipeRegistry.listSortedById 保证遍历序稳定、可复现）。
 *
 * <p><b>先判定后消耗（用户要求6）</b>：CraftPlan.plan 全槽求解成功才产出计划，
 * 任一槽不足=整体放弃零扣减。
 */
public final class DropCraftHandler {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("cnpcplus");

    /** 玩家丢出、尚未落地检查的实体 id → 所在 Level（维度匹配用，防跨世界误清）。 */
    private static final Map<Integer, net.minecraft.world.level.Level> PENDING = new HashMap<>();

    /** 「玩家丢出」身份集合（合成输入过滤用；Leave 事件清理；重启后清空=老物品需重丢）。 */
    private static final Set<Integer> PLAYER_DROPPED = new HashSet<>();

    /** 单次触发的链式合成防御上限（正常必在原料耗尽前退出）。 */
    private static final int CHAIN_LIMIT = 64;

    /**
     * 233d-UI8 重试窗口（tick）：自丢出起 200t（=10 秒）内落地检查失败每 tick 重试。
     * 也兜底「永远不落地」的飞行实体（被水冲走/卡墙/虚空掉落前清理，防 PENDING 泄漏）。
     */
    private static final int RETRY_WINDOW_TICKS = 200;

    private DropCraftHandler() {}

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        ItemEntity item = event.getEntity();
        PENDING.put(item.getId(), item.level());
        PLAYER_DROPPED.add(item.getId());
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity)) return;
        PENDING.remove(event.getEntity().getId());
        PLAYER_DROPPED.remove(event.getEntity().getId());
    }

    /**
     * 233d-UI9：关服清静态身份集合（CnpcPlus ServerStopped 调用，用户拍板「只加关服清理」）。
     * 单人退出存档若实体未 fire EntityLeaveLevelEvent，残留实体 id 会被下个存档复用
     * （实体 id 计数每档从头开始），误认成「玩家丢出」参与合成——必须随服务器停止清空。
     * 与 DropRecipeRegistry.clearCache 同批：先 awaitDiskPending 再清配方，再调本方法。
     */
    public static void clearStatics() {
        PENDING.clear();
        PLAYER_DROPPED.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (PENDING.isEmpty()) return; // O(1) 短路：绝大多数 tick 无待查实体，零开销
        if (!(event.level instanceof ServerLevel level)) return;
        // 快照遍历：tryCraft 内部实体增删不影响本轮迭代
        for (Map.Entry<Integer, net.minecraft.world.level.Level> en : List.copyOf(PENDING.entrySet())) {
            if (en.getValue() != level) continue; // 其他维度的 pending，本维度 tick 不处理
            Integer id = en.getKey();
            net.minecraft.world.entity.Entity found = level.getEntity(id);
            if (found == null) {
                PENDING.remove(id); // 已消亡（Leave 事件双保险兜底）
                continue;
            }
            if (!(found instanceof ItemEntity item)) {
                PENDING.remove(id);
                continue;
            }
            if (!item.onGround()) {
                // 233d-UI8：飞行超 200t=永不落地（水冲/卡墙/虚空），兜底移除防泄漏
                if (item.tickCount > RETRY_WINDOW_TICKS) PENDING.remove(id);
                continue; // 飞行中不查，下个 tick 再看
            }
            // 233d-UI8 根因修复（用户「经常不触发要反复重丢好几次」）：
            // 原实现落地只查一次、失败即永久移除——另一物品还在空中/晚几 tick
            // 落地、或被合并进同组时该组永不重查=「条件正常却不触发」。
            // 改为：落地后每 tick 重试 tryCraft，合成成功或超 200t 窗口才移除
            // （实体被捡走/消亡由 EntityLeaveLevelEvent 双清；≥2 实体规则不动）。
            boolean crafted = tryCraft(level, item);
            if (crafted || item.tickCount > RETRY_WINDOW_TICKS) {
                PENDING.remove(id);
            }
        }
    }

    /**
     * 以触发实体所在方块为合成场：同格过滤玩家丢出的实体 → 链式合成。
     *
     * @return 233d-UI8 本 tick 是否发生至少一次合成（PENDING 重试窗判定：false=
     *         继续每 tick 重查，true=已合成可移除）。
     */
    private static boolean tryCraft(ServerLevel level, ItemEntity trigger) {
        BlockPos pos = trigger.blockPosition();
        AABB box = new AABB(pos);
        List<DropRecipeData> recipes = DropRecipeRegistry.listSortedById(); // id 升序=稳定优先级
        if (recipes.isEmpty()) return false;

        boolean any = false;
        for (int chain = 0; chain < CHAIN_LIMIT; chain++) {
            // 每轮重新收集：上一轮的消耗/新产出会改变格内状态
            List<ItemEntity> inputs = level.getEntitiesOfClass(ItemEntity.class, box).stream()
                    .filter(e -> PLAYER_DROPPED.contains(e.getId()) && !e.getItem().isEmpty())
                    .toList();
            if (inputs.size() < 2) return any; //「重合」=同格 ≥2 个玩家丢出实体（用户要求2）

            CraftPlan plan = null;
            List<Integer> matched = new ArrayList<>(); // 233d-UI8 诊断：本格全部命中配方 id
            for (DropRecipeData r : recipes) { // id 升序遍历，第一条命中即用（稳定优先级）
                if (!boundOk(r, level, pos)) continue; // 233b：维度/坐标绑定过滤（空=不限）
                CraftPlan p = CraftPlan.plan(inputs, r);
                if (p == null) continue;
                matched.add(r.id);
                if (plan == null) plan = p; // 选用仍=第一条命中（id 升序，规则不变）
            }
            if (plan == null) return any;
            // 233d-UI8 诊断（用户拍板授权日志）：同格命中多配方=「音效没绑/旧形状」
            // 头号嫌疑（id 升序选中了更早的旧配方）。仅链首打印防刷屏。
            if (matched.size() > 1 && chain == 0) {
                LOGGER.debug("[drop] 同格命中多配方 ids={} 按id升序选用 id={} pos=({},{},{})",
                        matched, plan.recipe.id, pos.getX(), pos.getY(), pos.getZ());
            }

            // 233d-UI6 粒子锚点：消耗前先快照触发掉落物实体的当前位置
            // （链式循环中 trigger 实体可能已被 shrink/discard，必须先取
            // Vec3 值快照；用户要求粒子以「合成时掉落物所在位置」发射，
            // 不是方块中心。音效仍留方块中心——用户只反馈粒子位置）
            net.minecraft.world.phys.Vec3 anchor = trigger.position();
            // 先判定后消耗：plan 已验证全部充足，这里只按计划扣减
            execute(level, pos, anchor, plan);
            any = true;
            // 允许日志（用户授权）：链式只打第一条防刷屏
            if (chain == 0) {
                LOGGER.debug("[drop] 合成触发 pos=({},{},{}) id={} name={} result={}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        plan.recipe.id, plan.recipe.name,
                        plan.output.getCount() + "x" + plan.output.getItem());
            }
        }
        LOGGER.debug("[drop] 链式合成达到防御上限 pos=({},{},{})（配方疑似零消耗，已截断）",
                pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    /**
     * 233b 生效绑定校验（安全保证：只收集指定维度、指定方块坐标内的物品实体）。
     * dim 空=不限（兼容 232 存量）；dim 非空须与触发维度一致；hasBindPos 再校验触发方块坐标。
     * 不通过=该配方静默跳过（不是错误）。
     */
    private static boolean boundOk(DropRecipeData r, ServerLevel level, BlockPos pos) {
        if (r.dim == null || r.dim.isEmpty()) return true;
        if (!level.dimension().location().toString().equals(r.dim)) return false;
        return !r.hasBindPos || (r.bindX == pos.getX() && r.bindY == pos.getY() && r.bindZ == pos.getZ());
    }

    /**
     * @param anchor 233d-UI6 粒子发射锚点=合成触发掉落物实体位置（消耗前快照）；
     *               方块坐标 pos 仍用于产出位置与音效位置（不变）。
     */
    private static void execute(ServerLevel level, BlockPos pos,
                                net.minecraft.world.phys.Vec3 anchor, CraftPlan plan) {
        for (Map.Entry<ItemEntity, Integer> en : plan.deduct.entrySet()) {
            ItemEntity e = en.getKey();
            if (e.isRemoved()) continue; // 并发窗口兜底（单线程 tick 内基本不会发生）
            shrink(e, en.getValue());
        }
        // 产出：丢在方块中心；不在 PLAYER_DROPPED=不会被当输入、拾取延迟防立即回捡
        ItemEntity out = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, plan.output);
        out.setDefaultPickUpDelay();
        level.addFreshEntity(out);
        // 233d 粒子特效：实例快照入队（引擎 LevelTick 驱动发送；fx 空=零开销）
        // 233d-UI6：锚点=掉落物实体位置 anchor（用户「以被合成掉落物实体世界坐标为锚点」）
        top.cnpcplus.drop.fx.runtime.DropFxEngine.spawn(level,
                anchor.x, anchor.y, anchor.z, plan.recipe.fxs);
        // 233d 合成音效：全房间可闻；音量/音高固定 1.0（用户确认）。
        // 233d-UI9 修（用户拍板「照样修」）：旧实现查 BuiltInRegistries.SOUND_EVENT——
        // 但音效选择器的候选=客户端 SoundManager.getAvailableSounds()（sounds.json 汇总），
        // CNPC 自有声（如 customnpcs:gun.pistol.shot）与资源包声从不进该 registry=必被拒。
        // 照搬 vanilla /playsound（PlaySoundCommand:56 同款）：Holder.direct 任意 rl 直发包
        // （ServerLevel.playSeededSound 无查表；FriendlyByteBuf writeId DIRECT 分支安全传输；
        // 客户端 handleSoundEvent 按自己 sounds.json 播）——专服可用，只发字符串 id。
        // 格式非法仅记 debug 不崩；客户端真没有该声=静默（与 /playsound 行为一致）。
        String snd = plan.recipe.soundId;
        if (snd != null && !snd.isEmpty()) {
            try {
                net.minecraft.resources.ResourceLocation rl =
                        new net.minecraft.resources.ResourceLocation(snd);
                net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> holder =
                        net.minecraft.core.Holder.direct(
                                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl));
                level.playSeededSound(null,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        holder, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F,
                        level.getRandom().nextLong());
                // 233d-UI6 打点：音效确实发出（用户「绑定音效完全没有」排查）
                LOGGER.debug("[drop] 播放合成音效 id={} pos=({},{},{})", snd,
                        pos.getX(), pos.getY(), pos.getZ());
            } catch (RuntimeException e) {
                LOGGER.debug("[drop] 音效 id 格式非法 {}", snd);
            }
        }
    }

    /** 实体扣减：shrink 后 setItem（服务端 DATA 同步），空则 discard。 */
    private static void shrink(ItemEntity e, int amount) {
        net.minecraft.world.item.ItemStack s = e.getItem().copy();
        s.shrink(amount);
        if (s.isEmpty()) {
            e.discard();
        } else {
            e.setItem(s);
        }
    }
}
