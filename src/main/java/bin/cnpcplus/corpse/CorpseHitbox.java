package bin.cnpcplus.corpse;

import bin.cnpcplus.config.CnpcPlusConfig;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 「固定点位重生的 NPC 死亡后尸体不该有碰撞箱」的共用判定。
 *
 * <p>判据与 1.12.2 已验证实现一致，用 {@code ais.returnToStart}（AI 设置里的
 * 「返回起点」开关）而<b>不是</b> {@code stats.spawnCycle}：
 * 后者是「什么时段重生」，与「在哪里重生」无关，拿它当条件会影响到不该影响的 NPC
 * （比如就地重生、尸体留在死亡地点本来就合理的那些）。
 * 哈基彬要的是「固定点位重生的 npc」，而 {@code returnToStart} 为真时
 * {@code reset():1227-1229} 才会把 NPC 传回起点 —— 这类 NPC 的尸体必然留在
 * 与重生点无关的位置，挡路且无意义。
 *
 * <p>放在 mixin 包之外，供 EntityNPCInterface 与 EntityCustomNpc 两个混入共用 ——
 * 后者覆写了 {@code getDimensions}，且在有 modelData 实体时根本不调 super，
 * 所以必须两处都拦，只拦一个会漏掉一半 NPC。
 */
public final class CorpseHitbox {

    private CorpseHitbox() {
    }

    /** 当前是否处于「固定点位重生的 NPC 的尸体停留阶段」。 */
    public static boolean isRestingCorpse(EntityNPCInterface npc) {
        if (npc == null) return false;
        if (!CnpcPlusConfig.CORPSE_NO_COLLISION.get()) return false;
        if (!npc.isKilled()) return false;
        // 只对「返回起点为开」的 NPC 生效，理由见类注释。
        return npc.ais != null && npc.ais.returnToStart;
    }
}
