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
            if (!item.onGround()) continue; // 飞行中不查，下个 tick 再看
            PENDING.remove(id);
            tryCraft(level, item);
        }
    }

    /** 以触发实体所在方块为合成场：同格过滤玩家丢出的实体 → 链式合成。 */
    private static void tryCraft(ServerLevel level, ItemEntity trigger) {
        BlockPos pos = trigger.blockPosition();
        AABB box = new AABB(pos);
        List<DropRecipeData> recipes = DropRecipeRegistry.listSortedById(); // id 升序=稳定优先级
        if (recipes.isEmpty()) return;

        for (int chain = 0; chain < CHAIN_LIMIT; chain++) {
            // 每轮重新收集：上一轮的消耗/新产出会改变格内状态
            List<ItemEntity> inputs = level.getEntitiesOfClass(ItemEntity.class, box).stream()
                    .filter(e -> PLAYER_DROPPED.contains(e.getId()) && !e.getItem().isEmpty())
                    .toList();
            if (inputs.size() < 2) return; //「重合」=同格 ≥2 个玩家丢出实体（用户要求2）

            CraftPlan plan = null;
            for (DropRecipeData r : recipes) { // id 升序遍历，第一条命中即用（稳定优先级）
                if (!boundOk(r, level, pos)) continue; // 233b：维度/坐标绑定过滤（空=不限）
                plan = CraftPlan.plan(inputs, r);
                if (plan != null) break;
            }
            if (plan == null) return;

            // 233d-UI6 粒子锚点：消耗前先快照触发掉落物实体的当前位置
            // （链式循环中 trigger 实体可能已被 shrink/discard，必须先取
            // Vec3 值快照；用户要求粒子以「合成时掉落物所在位置」发射，
            // 不是方块中心。音效仍留方块中心——用户只反馈粒子位置）
            net.minecraft.world.phys.Vec3 anchor = trigger.position();
            // 先判定后消耗：plan 已验证全部充足，这里只按计划扣减
            execute(level, pos, anchor, plan);
            // 允许日志（用户授权）：链式只打第一条防刷屏
            if (chain == 0) {
                LOGGER.info("[drop] 合成触发 pos=({},{},{}) id={} name={} result={}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        plan.recipe.id, plan.recipe.name,
                        plan.output.getCount() + "x" + plan.output.getItem());
            }
        }
        LOGGER.warn("[drop] 链式合成达到防御上限 pos=({},{},{})（配方疑似零消耗，已截断）",
                pos.getX(), pos.getY(), pos.getZ());
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
        // 233c 合成音效：全房间可闻；音量/音高固定 1.0（用户确认）；坏 id 仅 WARN 不崩
        String snd = plan.recipe.soundId;
        if (snd != null && !snd.isEmpty()) {
            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                    .getOptional(new net.minecraft.resources.ResourceLocation(snd))
                    .ifPresentOrElse(
                            ev -> {
                                level.playSound(null,
                                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                        ev, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                                // 233d-UI6 打点：音效确实播了（用户「绑定音效完全没有」排查）
                                LOGGER.info("[drop] 播放合成音效 id={} pos=({},{},{})", snd,
                                        pos.getX(), pos.getY(), pos.getZ());
                            },
                            () -> LOGGER.warn("[drop] 音效 id 无效 {}", snd));
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
