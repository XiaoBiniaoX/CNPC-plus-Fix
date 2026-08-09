package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import noppes.npcs.util.ValueUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = DataDisplay.class, remap = false)
public class MixinDataDisplayVisibilitySync {

    @Shadow(remap = false)
    private int visible;

    @Shadow(remap = false)
    public EntityNPCInterface npc;

    /**
     * @author cnpcplus
     * @reason sync visibility to client
     */
    @Overwrite
    public void setVisible(int type) {
        this.visible = ValueUtil.CorrectInt(type, 0, 2);
        this.npc.updateClient = true;
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
