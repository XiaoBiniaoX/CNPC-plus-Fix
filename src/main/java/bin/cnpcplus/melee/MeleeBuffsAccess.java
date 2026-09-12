package bin.cnpcplus.melee;

import java.util.List;

/**
 * NPC 近战「附加效果」多 BUFF 数据的访问接口。
 *
 * <p>放在 mixin 包之外，避免 IllegalClassLoadError。
 */
public interface MeleeBuffsAccess {

    /** 返回可直接编辑的 BUFF 列表（顺序即界面显示顺序）。 */
    List<MeleeBuff> cnpcplus$getMeleeBuffs();

    /** 整表替换。 */
    void cnpcplus$setMeleeBuffs(List<MeleeBuff> buffs);
}
