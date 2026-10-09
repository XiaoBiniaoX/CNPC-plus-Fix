package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.blocks.tiles.TileWaypoint;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerQuestData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 优化 #12（1.20.1 主内容移植）：消除 {@code TileWaypoint} 定位扫描的双查询与列表差量运算。
 *
 * <h2>原版做了什么（javap 实证，1.12.2 05Jul20，func_73660_a）</h2>
 * 每 10 tick：
 * <pre>
 * toCheck = getPlayerList(range, range, range);            ← 查询 1（小盒子）
 * toCheck.removeAll(recentlyChecked);                      ← O(n×m) equals
 * outer   = getPlayerList(range+10, range+10, range+10);   ← 查询 2（大盒子）
 * recentlyChecked.retainAll(outer);                        ← O(n×m)
 * recentlyChecked.addAll(toCheck);
 * if (toCheck.isEmpty()) return;
 * for (p : toCheck) { ... 位置任务检查 ... }
 * </pre>
 * {@code getPlayerList(x,y,z)} = {@code world.getEntitiesWithinAABB(EntityPlayer,
 * new AxisAlignedBB(pos, pos.add(1,1,1)).grow(x,y,z))}——纯 AABB 查询，无距离过滤。
 *
 * <h2>问题在哪</h2>
 * 两次 AABB 查询，其中小盒子的结果完全可以由大盒子结果过滤得到；
 * {@code removeAll}/{@code retainAll} 对 List 是嵌套线性扫描。
 * 在路标范围大、在线玩家多的服务器上，这一段是可测量的周期性开销。
 *
 * <h2>本类怎么做</h2>
 * 只查一次大盒子（range+10），内层用
 * {@code innerBox.intersects(player.getEntityBoundingBox())} 过滤出等价的
 * range 集合；两个差量运算改用身份集合（玩家实体按引用判等，与原版
 * removeAll/retainAll 对实体列表的 equals 判定一致——实体 equals 即引用比较）。
 *
 * <p><b>行为逐项等价</b>：
 * <ul>
 *   <li>客户端/空名提前返回、ticks 递减与 10 tick 门控不变；</li>
 *   <li>{@code toCheck}、{@code recentlyChecked} 两个字段的写入语义不变
 *       （recentlyChecked 原地修剪+追加，toCheck 整体替换）；</li>
 *   <li>quest 检查循环逐行照抄（type==3 → QuestLocation.setFound →
 *       sendMessage(name + " " + I18n("quest.found")) → checkQuestCompletion(player,3)
 *       → updateClient = true）。</li>
 * </ul>
 * 玩家间的检查顺序可能与原版不同（过滤子集 vs 独立查询），但每个玩家的
 * 检查互相独立，顺序不影响结果。
 *
 * <h2>配置</h2>
 * {@code waypointScanOptimize}（默认 true）。关闭时取消注入，原版双查询路径运行。
 */
@Mixin(value = TileWaypoint.class, remap = false)
public abstract class MixinTileWaypointScan {

    @Shadow
    public String name;

    @Shadow
    private int ticks;

    @Shadow
    public int range;

    @Shadow
    private List<EntityPlayer> recentlyChecked;

    @Shadow
    private List<EntityPlayer> toCheck;

    @Shadow
    private List<EntityPlayer> getPlayerList(int x, int y, int z) {
        throw new AssertionError();
    }

    @Inject(method = "func_73660_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$singleScan(CallbackInfo ci) {
        if (!CnpcPlusConfig.isWaypointScanOptimizeEnabled()) {
            // 不 cancel：原版双查询路径原样运行。
            return;
        }
        ci.cancel();
        // 以下逐行对应原版 func_73660_a。
        TileWaypoint self = (TileWaypoint) (Object) this;
        if (self.getWorld().isRemote || this.name.isEmpty()) {
            return;
        }
        this.ticks--;
        if (this.ticks > 0) {
            return;
        }
        this.ticks = 10;

        // 原版查询 1（range）+ 查询 2（range+10）合并为一次 range+10 查询。
        List<EntityPlayer> outer = this.getPlayerList(this.range + 10, this.range + 10, this.range + 10);
        BlockPos pos = self.getPos();
        AxisAlignedBB innerBox = new AxisAlignedBB(pos, pos.add(1, 1, 1))
                .grow(this.range, this.range, this.range);

        Set<EntityPlayer> outerSet = cnpcplus$identitySet();
        List<EntityPlayer> toCheckNow = new ArrayList<EntityPlayer>();
        for (EntityPlayer p : outer) {
            outerSet.add(p);
            if (innerBox.intersects(p.getEntityBoundingBox())) {
                toCheckNow.add(p);
            }
        }

        // toCheck = inner - recentlyChecked（原版 removeAll 的语义）。
        Set<EntityPlayer> recentSet = cnpcplus$identitySet();
        for (EntityPlayer p : this.recentlyChecked) {
            recentSet.add(p);
        }
        for (int i = toCheckNow.size() - 1; i >= 0; i--) {
            if (recentSet.contains(toCheckNow.get(i))) {
                toCheckNow.remove(i);
            }
        }

        // recentlyChecked = (recentlyChecked ∩ outer) + toCheck（原版 retainAll + addAll）。
        List<EntityPlayer> newRecent = new ArrayList<EntityPlayer>();
        for (EntityPlayer p : this.recentlyChecked) {
            if (outerSet.contains(p)) {
                newRecent.add(p);
            }
        }
        newRecent.addAll(toCheckNow);
        this.recentlyChecked.clear();
        this.recentlyChecked.addAll(newRecent);

        this.toCheck = toCheckNow;
        if (toCheckNow.isEmpty()) {
            return;
        }

        for (EntityPlayer player : toCheckNow) {
            PlayerData pdata = PlayerData.get(player);
            PlayerQuestData qdata = pdata.questData;
            for (Object o : qdata.activeQuests.values()) {
                QuestData qd = (QuestData) o;
                if (qd.quest.type != 3) {
                    continue;
                }
                QuestLocation loc = (QuestLocation) qd.quest.questInterface;
                if (loc.setFound(qd, this.name)) {
                    player.sendMessage(new TextComponentTranslation(
                            new StringBuilder()
                                    .append(this.name)
                                    .append(" ")
                                    .append(I18n.translateToLocal("quest.found"))
                                    .toString(),
                            new Object[0]));
                    qdata.checkQuestCompletion(player, 3);
                    pdata.updateClient = true;
                }
            }
        }
    }

    @Unique
    private static Set<EntityPlayer> cnpcplus$identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<EntityPlayer, Boolean>());
    }
}
