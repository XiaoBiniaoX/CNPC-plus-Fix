package bin.cnpcplus.lines;

import noppes.npcs.controllers.data.Lines;

/**
 * 暴露 CNPCPlus 附加的近战打击台词数据。
 */
public interface MeleeHitLinesAccess {
    // 获取近战打击成功后使用的台词集合。
    Lines cnpcplus$getMeleeHitLines();
}
