package bin.cnpcplus.mixin.entity.data;

import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DataDisplay 缺失的脏标记补齐。
 *
 * <p>CNPC 原版 setMarkovGender/setMarkovGeneratorId/setOverlayGlowing/setShowingLayers/setBossColor
 * 均不置 npc.updateClient，导致这些属性变更不同步到客户端。
 */
@Mixin(value = DataDisplay.class, remap = false)
public class MixinDataDisplayDirtyFlags {

    @Shadow(remap = false)
    noppes.npcs.entity.EntityNPCInterface npc;

    @Inject(method = "setMarkovGender", at = @At("TAIL"), remap = false)
    private void cnpcplus$dirtyMarkovGender(CallbackInfo ci) {
        this.npc.updateClient = true;
    }

    @Inject(method = "setMarkovGeneratorId", at = @At("TAIL"), remap = false)
    private void cnpcplus$dirtyMarkovGeneratorId(CallbackInfo ci) {
        this.npc.updateClient = true;
    }

    @Inject(method = "setOverlayGlowing", at = @At("TAIL"), remap = false)
    private void cnpcplus$dirtyOverlayGlowing(CallbackInfo ci) {
        this.npc.updateClient = true;
    }

    @Inject(method = "setShowingLayers", at = @At("TAIL"), remap = false)
    private void cnpcplus$dirtyShowingLayers(CallbackInfo ci) {
        this.npc.updateClient = true;
    }

    @Inject(method = "setBossColor", at = @At("TAIL"), remap = false)
    private void cnpcplus$dirtyBossColor(CallbackInfo ci) {
        this.npc.updateClient = true;
    }
}
