package net.rizen.lazy_chunks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.util.PendingTaskQueueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

    @Inject(method = "runAllTasks", at = @At("HEAD"), cancellable = true, require = 0)
    private void lazychunks$throttleChunkLoading(CallbackInfo ci) {
        BlockableEventLoop<?> self = (BlockableEventLoop<?>) (Object) this;

        if (self != Minecraft.getInstance()) {
            return;
        }

        Queue<Runnable> pendingRunnables = PendingTaskQueueAccess.getPendingRunnables(self);
        if (pendingRunnables == null) {
            return;
        }

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
