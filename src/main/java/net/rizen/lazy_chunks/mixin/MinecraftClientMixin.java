package net.rizen.lazy_chunks.mixin;

import net.minecraft.client.MinecraftClient;
import net.rizen.lazy_chunks.LazyChunkLoading;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Unique
    private static long lazychunks$lastFrameNanos = 0;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void lazychunks$recordPerFrameFps(boolean tick, CallbackInfo ci) {
        long now = System.nanoTime();
        if (lazychunks$lastFrameNanos > 0) {
            long deltaNanos = now - lazychunks$lastFrameNanos;
            if (deltaNanos > 0) {
                double frameTimeMs = deltaNanos / 1_000_000.0;
                LazyChunkLoading.recordFrameTime(frameTimeMs);
            }
        }
        lazychunks$lastFrameNanos = now;
    }
}
