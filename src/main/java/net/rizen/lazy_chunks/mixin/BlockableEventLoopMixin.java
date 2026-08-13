package net.rizen.lazy_chunks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.TeleportDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

    private static volatile long lastFrameStartTime = 0L;

    @Inject(method = "runAllTasks", at = @At("HEAD"))
    private void lazychunks$recordFrameMetrics(CallbackInfo ci) {
        Object loop = this;
        if (!(loop instanceof Minecraft minecraft)) {
            return;
        }

        Minecraft instance = Minecraft.getInstance();
        if (instance == null || minecraft != instance) {
            return;
        }

        long currentTime = System.nanoTime();
        if (lastFrameStartTime > 0) {
            double frameTimeMs = (currentTime - lastFrameStartTime) / 1_000_000.0;
            LazyChunkLoading.recordFrameTime(frameTimeMs);
        }
        lastFrameStartTime = currentTime;

        if (minecraft.player == null || minecraft.level == null) {
            TeleportDetector.reset();
            return;
        }

        TeleportDetector.tick();
    }
}
