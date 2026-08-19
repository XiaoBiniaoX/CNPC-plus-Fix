package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobChunkLoader;
import noppes.npcs.roles.JobInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.CnpcPlus;

/**
 * 区块加载者修复（JobChunkLoader）：
 * 原版 aiShouldExecute 依赖「玩家在 48 格内 + 10 分钟内看到过」，无玩家即不加载 → 完全无效。
 * 现在：每次 tick 强制加载 NPC 所在区块及其周围一圈（3x3），ticking=true 保持实体活跃，
 * 写 ForcedChunksSavedData 持久化，重进世界自动恢复。
 */
@Mixin(value = JobChunkLoader.class, remap = false)
public class MixinJobChunkLoader {

    @Inject(method = "aiShouldExecute", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$forceNpcChunks(CallbackInfoReturnable<Boolean> cir) {
        cnpcplus$refreshForcedChunks();
        cir.setReturnValue(false);
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void cnpcplus$unloadOnReset(CallbackInfo ci) {
        cnpcplus$releaseForcedChunks();
    }

    private void cnpcplus$refreshForcedChunks() {
        EntityNPCInterface npc = ((JobInterface) (Object) this).npc;
        if (npc == null) return;
        if (!(npc.level() instanceof ServerLevel level)) return;
        if (level.isClientSide) return;

        ChunkPos center = npc.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                ForgeChunkManager.forceChunk(level, CnpcPlus.MOD_ID, npc.getUUID(), chunk.x, chunk.z, true, true);
            }
        }
    }

    private void cnpcplus$releaseForcedChunks() {
        EntityNPCInterface npc = ((JobInterface) (Object) this).npc;
        if (npc == null) return;
        if (!(npc.level() instanceof ServerLevel level)) return;
        ChunkPos center = npc.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                ForgeChunkManager.forceChunk(level, CnpcPlus.MOD_ID, npc.getUUID(), chunk.x, chunk.z, false, true);
            }
        }
    }
}