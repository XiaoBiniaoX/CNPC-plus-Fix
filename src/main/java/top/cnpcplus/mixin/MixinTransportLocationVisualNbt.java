package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.TransportLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.TransportVisualMeta;

/**
 * 任务241：TransportLocation 增加 5 个可视化扩展键（Icon/IconSel/Image/ImageMode/Desc）。
 * 键随原生 writeNBT/readNBT 进出 transport.dat，NPC NBT 只存 TransporterId，完全不碰 NPC NBT。
 * 旧世界无这些键 → 默认值（无图标/无图/模式0/空介绍），向后兼容。
 */
@Mixin(value = TransportLocation.class, remap = false)
public class MixinTransportLocationVisualNbt implements TransportVisualMeta {

    @Unique private String cnpcplus$icon = "";
    @Unique private String cnpcplus$iconSel = "";
    @Unique private String cnpcplus$image = "";
    @Unique private int cnpcplus$imageMode = TransportKeys.MODE_FILL;
    @Unique private String cnpcplus$desc = "";

    @Inject(method = "readNBT", at = @At("TAIL"), remap = false)
    private void cnpcplus$readVisual(CompoundTag compound, CallbackInfo ci) {
        if (compound == null) return;
        if (compound.contains(TransportKeys.ICON)) this.cnpcplus$icon = compound.getString(TransportKeys.ICON);
        if (compound.contains(TransportKeys.ICON_SEL)) this.cnpcplus$iconSel = compound.getString(TransportKeys.ICON_SEL);
        if (compound.contains(TransportKeys.IMAGE)) this.cnpcplus$image = compound.getString(TransportKeys.IMAGE);
        if (compound.contains(TransportKeys.IMAGE_MODE)) {
            this.cnpcplus$imageMode = compound.getInt(TransportKeys.IMAGE_MODE);
            if (this.cnpcplus$imageMode < 0 || this.cnpcplus$imageMode >= TransportKeys.MODE_COUNT) {
                this.cnpcplus$imageMode = TransportKeys.MODE_FILL;
            }
        }
        if (compound.contains(TransportKeys.DESC)) this.cnpcplus$desc = compound.getString(TransportKeys.DESC);
    }

    @Inject(method = "writeNBT", at = @At("TAIL"), remap = false,
            cancellable = true)
    private void cnpcplus$writeVisual(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag out = cir.getReturnValue();
        if (out == null) return;
        if (!this.cnpcplus$icon.isEmpty()) out.putString(TransportKeys.ICON, this.cnpcplus$icon);
        if (!this.cnpcplus$iconSel.isEmpty()) out.putString(TransportKeys.ICON_SEL, this.cnpcplus$iconSel);
        if (!this.cnpcplus$image.isEmpty()) out.putString(TransportKeys.IMAGE, this.cnpcplus$image);
        if (this.cnpcplus$imageMode != TransportKeys.MODE_FILL) out.putInt(TransportKeys.IMAGE_MODE, this.cnpcplus$imageMode);
        if (!this.cnpcplus$desc.isEmpty()) out.putString(TransportKeys.DESC, this.cnpcplus$desc);
    }

    @Override public String cnpcplus$getIcon() { return this.cnpcplus$icon; }
    @Override public void cnpcplus$setIcon(String v) { this.cnpcplus$icon = v == null ? "" : v; }
    @Override public String cnpcplus$getIconSel() { return this.cnpcplus$iconSel; }
    @Override public void cnpcplus$setIconSel(String v) { this.cnpcplus$iconSel = v == null ? "" : v; }
    @Override public String cnpcplus$getImage() { return this.cnpcplus$image; }
    @Override public void cnpcplus$setImage(String v) { this.cnpcplus$image = v == null ? "" : v; }
    @Override public int cnpcplus$getImageMode() { return this.cnpcplus$imageMode; }
    @Override public void cnpcplus$setImageMode(int v) {
        this.cnpcplus$imageMode = (v >= 0 && v < TransportKeys.MODE_COUNT) ? v : TransportKeys.MODE_FILL;
    }
    @Override public String cnpcplus$getDesc() { return this.cnpcplus$desc; }
    @Override public void cnpcplus$setDesc(String v) { this.cnpcplus$desc = v == null ? "" : v; }
}
