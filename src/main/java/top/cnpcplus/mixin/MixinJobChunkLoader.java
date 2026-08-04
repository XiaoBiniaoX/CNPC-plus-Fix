package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import noppes.npcs.controllers.ChunkController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobChunkLoader;
import noppes.npcs.roles.JobInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.CnpcPlus;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = JobChunkLoader.class, remap = false)
public class MixinJobChunkLoader {

    @Unique
    private final Set<ChunkPos> cnpcplus$forcedChunks = new HashSet<>();

    @Inject(method = "aiShouldExecute", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$forceNpcChunks(CallbackInfoReturnable<Boolean> cir) {
        this.cnpcplus$refreshForcedChunks();
        cir.setReturnValue(false);
    }

    @Inject(method = {"reset", "delete"}, at = @At("HEAD"))
    private void cnpcplus$unloadForcedChunks(CallbackInfo ci) {
        this.cnpcplus$unloadAllForcedChunks();
    }

    @Unique
    private void cnpcplus$refreshForcedChunks() {
        EntityNPCInterface npc = ((JobInterface) (Object) this).npc;
        if (!(npc.level() instanceof ServerLevel level)) return;

        ChunkPos center = npc.chunkPosition();
        Set<ChunkPos> wanted = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                wanted.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }

        for (ChunkPos chunk : wanted) {
            if (!this.cnpcplus$forcedChunks.contains(chunk)) {
                ForgeChunkManager.forceChunk(level, CnpcPlus.MOD_ID, npc.blockPosition(), chunk.x, chunk.z, true, true);
                ForgeChunkManager.forceChunk(level, CnpcPlus.MOD_ID, npc.getUUID(), chunk.x, chunk.z, true, true);
                ChunkController.instance.load(level, npc.getUUID(), chunk.x, chunk.z);
            }
        }
        for (ChunkPos chunk : new HashSet<>(this.cnpcplus$forcedChunks)) {
            if (!wanted.contains(chunk)) {
                ForgeChunkManager.forceChunk(level, CnpcPlus.MOD_ID, npc.blockPosition(), chunk.x, chunk.z, false, true);
                ForgeChunkManager.forceChunk(level, CnpcPlus.MOD_ID, npc.getUUID(), chunk.x, chunk.z, false, true);
                ChunkController.instance.unload(level, npc.getUUID(), chunk.x, chunk.z);
            }
        }

        this.cnpcplus$forcedChunks.clear();
        this.cnpcplus$forcedChunks.addAll(wanted);
    }

    @Unique
    private void cnpcplus$unloadAllForcedChunks() {
        EntityNPCInterface npc = ((JobInterface) (Object) this).npc;
        if (!(npc.level() instanceof ServerLevel level)) return;
        for (ChunkPos chunk : this.cnpcplus$forcedChunks) {
            ForgeChunkManager.forceChunk(level, CnpcPlus.MOD_ID, npc.blockPosition(), chunk.x, chunk.z, false, true);
            ForgeChunkManager.forceChunk(level, CnpcPlus.MOD_ID, npc.getUUID(), chunk.x, chunk.z, false, true);
            ChunkController.instance.unload(level, npc.getUUID(), chunk.x, chunk.z);
        }
        this.cnpcplus$forcedChunks.clear();
    }
}
