package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.TransportCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transport.TransportPanelMeta;
import top.cnpcplus.transport.TransportPanelStyle;

/**
 * 任务241j：TransportCategory 增加面板样式标签（cnpcplusPanel）。
 * 随原生 readNBT/writeNBT 进出 transport.dat；旧世界无此键 → 空标签，读取时按默认值兜底。
 */
@Mixin(value = TransportCategory.class, remap = false)
public class MixinTransportCategoryPanelNbt implements TransportPanelMeta {

    @Unique private CompoundTag cnpcplus$panel;

    @Inject(method = "readNBT", at = @At("TAIL"), remap = false)
    private void cnpcplus$readPanel(CompoundTag compound, CallbackInfo ci) {
        if (compound != null && compound.contains(TransportPanelStyle.NBT_KEY, 10)) {
            this.cnpcplus$panel = compound.getCompound(TransportPanelStyle.NBT_KEY);
        }
    }

    @Inject(method = "writeNBT", at = @At("TAIL"), remap = false)
    private void cnpcplus$writePanel(CompoundTag compound, CallbackInfo ci) {
        if (compound != null && this.cnpcplus$panel != null && !this.cnpcplus$panel.isEmpty()) {
            compound.put(TransportPanelStyle.NBT_KEY, this.cnpcplus$panel.copy());
        }
    }

    @Override
    public CompoundTag cnpcplus$panel() {
        return this.cnpcplus$panel;
    }

    @Override
    public void cnpcplus$setPanel(CompoundTag tag) {
        this.cnpcplus$panel = tag;
    }
}
