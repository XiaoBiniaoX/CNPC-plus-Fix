package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.accessor.LinkedScriptSyncAccess;
import top.cnpcplus.persist.NpcScriptExternalStore;

/**
 * NPC 脚本三键外置（照 {@code MixinEntityNPCMerchantExternal} 模板，同目标同类注入点）。
 *
 * <p>读档 RETURN：原生 {@code script.load} 跑完后，外置文件就位则把三键补进 tag 并补一次
 * {@code script.load}；linked「同步标签」含脚本时不能覆盖（共享脚本权威，由
 * {@code MixinLinkedNpcController.loadScripts} 在原生体内先灌好）。
 *
 * <p>存档 RETURN：{@code script.save} 已把三键写进 tag，这里外置落盘后摘除。
 * linked 的共享快照走 {@code readNpcData}（不经过 m_7380_），不受摘除影响。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCScriptsExternal {

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), require = 1, remap = false)
    private void cnpcplus$restoreExternalScripts(CompoundTag tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!NpcScriptExternalStore.prepareLoad(npc, tag)) return;
        if (npc.linkedData != null && npc.linkedData.data != null
                && ((LinkedScriptSyncAccess) (Object) npc.linkedData).cnpcplus$isScriptSync()
                && (npc.linkedData.data.contains("Scripts", 9)
                    || npc.linkedData.data.contains("ScriptEnabled"))) return;
        npc.script.load(tag);
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), require = 1, remap = false)
    private void cnpcplus$externalizeScripts(CompoundTag tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        NpcScriptExternalStore.saveNpc(npc, tag);
    }
}
