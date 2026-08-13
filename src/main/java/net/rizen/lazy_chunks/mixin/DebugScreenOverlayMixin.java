package net.rizen.lazy_chunks.mixin;

import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.util.Formatting;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.TeleportDetector;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugHud.class)
public class DebugScreenOverlayMixin {

    @Unique
    private static long lazychunks$lastUpdateTime = 0;
    @Unique
    private static final long UPDATE_INTERVAL_MS = 1500;
    @Unique
    private static int lazychunks$cachedPending = 0;
    @Unique
    private static double lazychunks$cachedWeight = 0;
    @Unique
    private static double lazychunks$cachedBudget = 0;
    @Unique
    private static int lazychunks$cachedProcessed = 0;
    @Unique
    private static double lazychunks$cachedProcessingTime = 0;
    @Unique
    private static double lazychunks$cachedQueueGrowth = 0;
    @Unique
    private static boolean lazychunks$cachedTeleportRecovery = false;
    @Unique
    private static double lazychunks$cachedOnePercentLowFps = 0;
    @Unique
    private static int lazychunks$cachedExtraThrottleCores = 0;
    @Unique
    private static double lazychunks$cachedLowOnePercentDuration = 0;

    @Inject(method = "getRightText", at = @At("RETURN"), require = 0)
    private void lazychunks$addDebugInfo(CallbackInfoReturnable<List<String>> cir) {
        List<String> info = cir.getReturnValue();
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        if (!config.showDebugOverlay) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lazychunks$lastUpdateTime > UPDATE_INTERVAL_MS) {
            lazychunks$lastUpdateTime = now;
            lazychunks$cachedPending = LazyChunkLoading.getLastPendingTasks();
            lazychunks$cachedWeight = LazyChunkLoading.getLastWeight();
            lazychunks$cachedBudget = LazyChunkLoading.getLastBudget();
            lazychunks$cachedProcessed = LazyChunkLoading.getLastProcessed();
            lazychunks$cachedProcessingTime = LazyChunkLoading.getLastProcessingTimeMs();
            lazychunks$cachedQueueGrowth = LazyChunkLoading.getQueueGrowthRate();
            lazychunks$cachedTeleportRecovery = TeleportDetector.isTeleportRecovery();
            lazychunks$cachedOnePercentLowFps = LazyChunkLoading.getOnePercentLowFps();
            lazychunks$cachedExtraThrottleCores = LazyChunkLoading.getDynamicExtraThrottleCores();
            lazychunks$cachedLowOnePercentDuration = LazyChunkLoading.getLowOnePercentDurationSeconds();
        }

        int insertIndex = 0;
        info.add(insertIndex++, Formatting.YELLOW.toString() + Formatting.BOLD + "LazyChunks " + LazyChunksMod.VERSION);

        if (config.lazyChunkLoadingEnabled) {
            info.add(insertIndex++, String.format("Pending: %d | Weight: %.1f | Budget: %.1f",
                    lazychunks$cachedPending,
                    lazychunks$cachedWeight,
                    lazychunks$cachedBudget));

            info.add(insertIndex++, String.format("1%% Low: %.0f | Core+: %d",
                    lazychunks$cachedOnePercentLowFps,
                    lazychunks$cachedExtraThrottleCores));

            info.add(insertIndex++, String.format("Process: %.2fms | Low Time: %.0fs",
                    lazychunks$cachedProcessingTime,
                    lazychunks$cachedLowOnePercentDuration));

            StringBuilder status = new StringBuilder();
            if (LazyChunkLoading.wasThrottled()) {
                status.append(Formatting.GREEN).append("Throttling");
            } else {
                status.append(Formatting.GRAY).append("Idle");
            }

            if (lazychunks$cachedQueueGrowth > 5) {
                status.append(Formatting.GOLD).append(" [Queue+]");
            }

            if (lazychunks$cachedExtraThrottleCores > 0) {
                status.append(Formatting.AQUA).append(" [Core+").append(lazychunks$cachedExtraThrottleCores).append("]");
            }

            if (lazychunks$cachedTeleportRecovery) {
                status.append(Formatting.RED).append(" [Teleport]");
            }

            info.add(insertIndex++, status.toString());
        } else {
            info.add(insertIndex++, Formatting.GRAY + "Disabled");
        }

        info.add(insertIndex, "");
    }
}
