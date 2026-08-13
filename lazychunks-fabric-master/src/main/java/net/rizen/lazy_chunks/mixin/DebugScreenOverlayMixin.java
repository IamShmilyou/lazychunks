package net.rizen.lazy_chunks.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.TeleportDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Unique
    private static long lazychunks$lastUpdateTime = 0;
    @Unique
    private static final long UPDATE_INTERVAL_MS = 1000;
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
    private static int lazychunks$cachedAdaptiveExtraCores = 0;
    @Unique
    private static double lazychunks$cachedLowTimeDuration = 0;
    @Unique
    private static double lazychunks$cachedAdaptiveLowDuration = 0;

    @Inject(method = "getSystemInformation", at = @At("RETURN"), require = 0)
    private void lazychunks$addDebugInfo(CallbackInfoReturnable<List<String>> cir) {
        List<String> info = cir.getReturnValue();
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        if (!config.showDebugInfo) {
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
            lazychunks$cachedAdaptiveExtraCores = LazyChunkLoading.getAdaptiveExtraCores();
            lazychunks$cachedLowTimeDuration = LazyChunkLoading.getLowTimeDurationSeconds();
            lazychunks$cachedAdaptiveLowDuration = LazyChunkLoading.getAdaptiveLowDurationSeconds();
        }

        int insertIndex = 0;
        info.add(insertIndex++, ChatFormatting.YELLOW.toString() + ChatFormatting.BOLD + "LazyChunks " + LazyChunksMod.VERSION);

        if (config.lazyChunkLoadingEnabled) {
            info.add(insertIndex++, String.format("Pending: %d | Weight: %.1f | Budget: %.1f",
                    lazychunks$cachedPending,
                    lazychunks$cachedWeight,
                    lazychunks$cachedBudget));

            info.add(insertIndex++, String.format("1%% Low FPS: %.0f | Process: %.2fms",
                    lazychunks$cachedOnePercentLowFps,
                    lazychunks$cachedProcessingTime));

            StringBuilder status = new StringBuilder();
            if (LazyChunkLoading.wasThrottled()) {
                status.append(ChatFormatting.GREEN).append("Throttling");
            } else {
                status.append(ChatFormatting.GRAY).append("Idle");
            }

            if (lazychunks$cachedQueueGrowth > 5) {
                status.append(ChatFormatting.GOLD).append(" [Queue+]");
            }

            if (lazychunks$cachedTeleportRecovery) {
                status.append(ChatFormatting.RED).append(" [Teleport]");
            }

            if (lazychunks$cachedAdaptiveExtraCores > 0) {
                status.append(ChatFormatting.AQUA).append(" [Core +").append(lazychunks$cachedAdaptiveExtraCores).append("]");
            } else if (lazychunks$cachedLowTimeDuration > 0) {
                status.append(ChatFormatting.DARK_AQUA)
                        .append(String.format(" [Low %.0fs]", lazychunks$cachedLowTimeDuration));
            }

            info.add(insertIndex++, status.toString());
        } else {
            info.add(insertIndex++, ChatFormatting.GRAY + "Disabled");
        }

        info.add(insertIndex, "");
    }
}
