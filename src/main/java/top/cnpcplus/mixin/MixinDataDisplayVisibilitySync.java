package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import noppes.npcs.util.ValueUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * S9（Low）：补回 setVisible 相等短路。
 * 旧 @Overwrite 去掉了 type != visible 判断，每次调用都置 updateClient。
 * GUI 调用频率低可接受，但短路是零成本正确性优化。
 */
@Mixin(value = DataDisplay.class, remap = false)
public class MixinDataDisplayVisibilitySync {

    @Shadow(remap = false)
    private int visible;

    @Shadow(remap = false)
    EntityNPCInterface npc;

    /**
     * @author cnpcplus
     * @reason sync visibility to client + equality short-circuit
     */
    @Overwrite
    public void setVisible(int type) {
        int corrected = ValueUtil.CorrectInt(type, 0, 2);
        if (corrected != this.visible) {
            this.visible = corrected;
            this.npc.updateClient = true;
        }
    }

    /**
     * @author cnpcplus
     * @reason sync visibility to client
     */
    @Overwrite
    public int getVisible() {
        return this.visible;
    }
}
