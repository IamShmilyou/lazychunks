package net.rizen.lazy_chunks.mixin;

import net.minecraft.client.Minecraft;
import net.rizen.lazy_chunks.LazyChunkLoading;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    // ✅ 记录上一帧的系统时间戳
    private static long lastFrameTimeNs = 0;

    // 注入到主循环末尾，计算真实帧间隔
    @Inject(method = "runTick", at = @At("RETURN"))
    private void lazychunks$recordRealFrameInterval(boolean renderWorld, CallbackInfo ci) {
        long currentTimeNs = System.nanoTime();

        // 跳过第一帧（没有上一帧时间）
        if (lastFrameTimeNs == 0) {
            lastFrameTimeNs = currentTimeNs;
            return;
        }

        // ✅ 计算真实的帧间隔时间（与Sodium完全相同的方式）
        long frameIntervalNs = currentTimeNs - lastFrameTimeNs;
        lastFrameTimeNs = currentTimeNs;

        // 记录真实帧间隔
        LazyChunkLoading.recordFrameIntervalNs(frameIntervalNs);
        if (LazyChunkLoading.shouldTrackVanillaAverageFps()) {
            LazyChunkLoading.recordVanillaAverageFps(((Minecraft) (Object) this).getFps());
        }
    }
}
