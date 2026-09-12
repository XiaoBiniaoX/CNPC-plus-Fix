package bin.cnpcplus.corpse;

import bin.cnpcplus.config.CnpcPlusConfig;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 「会重生的 NPC 死亡后尸体不该有碰撞箱」的共用判定。
 *
 * <p>放在 mixin 包之外，供 EntityNPCInterface 与 EntityCustomNpc 两个混入共用 ——
 * 后者覆写了 {@code getDimensions}，且在有 modelData 实体时根本不调 super，
 * 所以必须两处都拦，只拦一个会漏掉一半 NPC。
 */
public final class CorpseHitbox {

    private CorpseHitbox() {
    }

    /** 当前是否处于「会重生的 NPC 的尸体停留阶段」。 */
    public static boolean isRestingCorpse(EntityNPCInterface npc) {
        if (npc == null) return false;
        if (!CnpcPlusConfig.CORPSE_NO_COLLISION.get()) return false;
        if (!npc.isKilled()) return false;
        if (npc.stats == null) return false;
        // spawnCycle 3/4 是「死后消失」，不存在尸体停留阶段，不插手。
        return npc.stats.spawnCycle != 3 && npc.stats.spawnCycle != 4;
    }
}
