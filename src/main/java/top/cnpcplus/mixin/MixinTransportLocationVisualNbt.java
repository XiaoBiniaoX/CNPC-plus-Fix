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
    @Unique private int cnpcplus$iconSize = 100;
    @Unique private int cnpcplus$imageZoom = 100;
    @Unique private int cnpcplus$textColor = -1;
    @Unique private int cnpcplus$textSize = -1;
    @Unique private int cnpcplus$imgX = 0;
    @Unique private int cnpcplus$imgY = 0;
    @Unique private int cnpcplus$imgScale = 100;
    @Unique private int cnpcplus$srcW = 0;
    @Unique private int cnpcplus$srcH = 0;
    @Unique private String cnpcplus$descSpans = "";
    @Unique private String cnpcplus$nameSpans = "";

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
        if (compound.contains(TransportKeys.ICON_SIZE)) {
            this.cnpcplus$iconSize = clampPct(compound.getInt(TransportKeys.ICON_SIZE));
        }
        if (compound.contains(TransportKeys.IMAGE_ZOOM)) {
            this.cnpcplus$imageZoom = clampPct(compound.getInt(TransportKeys.IMAGE_ZOOM));
        }
        if (compound.contains(TransportKeys.TEXT_COLOR)) {
            this.cnpcplus$textColor = compound.getInt(TransportKeys.TEXT_COLOR);
        }
        if (compound.contains(TransportKeys.TEXT_SIZE)) {
            this.cnpcplus$textSize = compound.getInt(TransportKeys.TEXT_SIZE);
        }
        if (compound.contains(TransportKeys.IMG_X)) this.cnpcplus$imgX = compound.getInt(TransportKeys.IMG_X);
        if (compound.contains(TransportKeys.IMG_Y)) this.cnpcplus$imgY = compound.getInt(TransportKeys.IMG_Y);
        if (compound.contains(TransportKeys.IMG_SCALE)) this.cnpcplus$imgScale = compound.getInt(TransportKeys.IMG_SCALE);
        if (compound.contains(TransportKeys.SRC_W)) this.cnpcplus$srcW = compound.getInt(TransportKeys.SRC_W);
        if (compound.contains(TransportKeys.SRC_H)) this.cnpcplus$srcH = compound.getInt(TransportKeys.SRC_H);
        if (compound.contains(TransportKeys.DESC_SPANS)) this.cnpcplus$descSpans = compound.getString(TransportKeys.DESC_SPANS);
        if (compound.contains(TransportKeys.NAME_SPANS)) this.cnpcplus$nameSpans = compound.getString(TransportKeys.NAME_SPANS);
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
        if (this.cnpcplus$iconSize != 100) out.putInt(TransportKeys.ICON_SIZE, this.cnpcplus$iconSize);
        if (this.cnpcplus$imageZoom != 100) out.putInt(TransportKeys.IMAGE_ZOOM, this.cnpcplus$imageZoom);
        if (this.cnpcplus$textColor >= 0) out.putInt(TransportKeys.TEXT_COLOR, this.cnpcplus$textColor);
        if (this.cnpcplus$textSize > 0) out.putInt(TransportKeys.TEXT_SIZE, this.cnpcplus$textSize);
        if (this.cnpcplus$imgX != 0) out.putInt(TransportKeys.IMG_X, this.cnpcplus$imgX);
        if (this.cnpcplus$imgY != 0) out.putInt(TransportKeys.IMG_Y, this.cnpcplus$imgY);
        if (this.cnpcplus$imgScale != 100) out.putInt(TransportKeys.IMG_SCALE, this.cnpcplus$imgScale);
        if (this.cnpcplus$srcW != 0) out.putInt(TransportKeys.SRC_W, this.cnpcplus$srcW);
        if (this.cnpcplus$srcH != 0) out.putInt(TransportKeys.SRC_H, this.cnpcplus$srcH);
        if (!this.cnpcplus$descSpans.isEmpty()) out.putString(TransportKeys.DESC_SPANS, this.cnpcplus$descSpans);
        if (!this.cnpcplus$nameSpans.isEmpty()) out.putString(TransportKeys.NAME_SPANS, this.cnpcplus$nameSpans);
    }

    private static int clampPct(int v) {
        return Math.max(25, Math.min(400, v));
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
    @Override public int cnpcplus$getIconSize() { return this.cnpcplus$iconSize; }
    @Override public void cnpcplus$setIconSize(int v) { this.cnpcplus$iconSize = clampPct(v); }
    @Override public int cnpcplus$getImageZoom() { return this.cnpcplus$imageZoom; }
    @Override public void cnpcplus$setImageZoom(int v) { this.cnpcplus$imageZoom = clampPct(v); }
    @Override public int cnpcplus$getTextColor() { return this.cnpcplus$textColor; }
    @Override public void cnpcplus$setTextColor(int v) { this.cnpcplus$textColor = v; }
    @Override public int cnpcplus$getTextSize() { return this.cnpcplus$textSize; }
    @Override public void cnpcplus$setTextSize(int v) { this.cnpcplus$textSize = v; }
    @Override public int cnpcplus$getImgX() { return this.cnpcplus$imgX; }
    @Override public void cnpcplus$setImgX(int v) { this.cnpcplus$imgX = v; }
    @Override public int cnpcplus$getImgY() { return this.cnpcplus$imgY; }
    @Override public void cnpcplus$setImgY(int v) { this.cnpcplus$imgY = v; }
    @Override public int cnpcplus$getImgScale() { return this.cnpcplus$imgScale; }
    @Override public void cnpcplus$setImgScale(int v) { this.cnpcplus$imgScale = v; }
    @Override public int cnpcplus$getSrcW() { return this.cnpcplus$srcW; }
    @Override public void cnpcplus$setSrcW(int v) { this.cnpcplus$srcW = v; }
    @Override public int cnpcplus$getSrcH() { return this.cnpcplus$srcH; }
    @Override public void cnpcplus$setSrcH(int v) { this.cnpcplus$srcH = v; }
    @Override public String cnpcplus$getDescSpans() { return this.cnpcplus$descSpans; }
    @Override public void cnpcplus$setDescSpans(String v) { this.cnpcplus$descSpans = v == null ? "" : v; }
    @Override public String cnpcplus$getNameSpans() { return this.cnpcplus$nameSpans; }
    @Override public void cnpcplus$setNameSpans(String v) { this.cnpcplus$nameSpans = v == null ? "" : v; }
}
