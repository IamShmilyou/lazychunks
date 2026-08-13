package net.rizen.lazy_chunks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.TeleportDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

    private static long lastFrameStartTime = 0;

    @Inject(method = "runAllTasks", at = @At("HEAD"), cancellable = true, require = 0)
    private void lazychunks$throttleChunkLoading(CallbackInfo ci) {
        BlockableEventLoop<?> self = (BlockableEventLoop<?>) (Object) this;

        if (self != Minecraft.getInstance()) {
            return;
        }

        long currentTime = System.nanoTime();
        if (lastFrameStartTime > 0) {
            double frameTimeMs = (currentTime - lastFrameStartTime) / 1_000_000.0;
            LazyChunkLoading.recordFrameTime(frameTimeMs);
        }
        lastFrameStartTime = currentTime;

        TeleportDetector.tick();

        Queue<Runnable> pendingRunnables = ((PendingTaskQueueAccessor) self).lazychunks$getPendingRunnables();

        int taskLimit = LazyChunkLoading.getTaskCount(pendingRunnables);
        if (!LazyChunkLoading.wasThrottled()) {
            return;
        }

        long maxTimeNanos = LazyChunkLoading.getMaxProcessingTimeNanos();
        long startTime = System.nanoTime();

        while (self.pollTask()) {
            --taskLimit;

            if (taskLimit <= 0) {
                break;
            }

            if (System.nanoTime() - startTime > maxTimeNanos) {
                break;
            }
        }

        double processingTimeMs = (System.nanoTime() - startTime) / 1_000_000.0;
        LazyChunkLoading.recordProcessingTime(processingTimeMs);

        ci.cancel();
    }
}
