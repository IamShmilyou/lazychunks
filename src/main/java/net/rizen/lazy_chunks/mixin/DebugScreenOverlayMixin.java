package net.rizen.lazy_chunks.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.TeleportDetector;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Unique
    private static volatile long lazychunks$lastUpdateTimeNanos = 0L;
    @Unique
    private static final long UPDATE_INTERVAL_NANOS = 2_000_000_000L;
    @Unique
    private static volatile List<String> lazychunks$cachedDebugBlock = null;

    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;Z)V",
                    ordinal = 1
            ),
            index = 1
    )
    private List<String> lazychunks$injectRightDebugLines(List<String> lines) {
        if (lines != null && LazyChunksConfig.getInstance().debugInfoEnabled) {
            lines.addAll(0, lazychunks$getDebugBlock());
        }
        return lines;
    }

    @Unique
    private static List<String> lazychunks$getDebugBlock() {
        long now = System.nanoTime();
        List<String> cached = lazychunks$cachedDebugBlock;
        if (cached == null || now - lazychunks$lastUpdateTimeNanos >= UPDATE_INTERVAL_NANOS) {
            cached = lazychunks$buildDebugInfo();
            lazychunks$cachedDebugBlock = cached;
            lazychunks$lastUpdateTimeNanos = now;
        }
        return cached;
    }

    @Unique
    private static List<String> lazychunks$buildDebugInfo() {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        List<String> info = new ArrayList<>();
        if (!config.debugInfoEnabled) {
            return List.of();
        }

        info.add(ChatFormatting.YELLOW.toString() + ChatFormatting.BOLD + "LazyChunks " + LazyChunksMod.VERSION);

        if (config.lazyChunkLoadingEnabled) {
            info.add(String.format(Locale.ROOT, "Pending: %d | Weight: %.1f | Budget: %.1f",
                    LazyChunkLoading.getLastPendingTasks(),
                    lazychunks$safeValue(LazyChunkLoading.getLastWeight()),
                    lazychunks$safeValue(LazyChunkLoading.getLastBudget())));

            int lowFps = LazyChunkLoading.getOnePercentLowFps();
            info.add(String.format(Locale.ROOT, "1%% Low FPS: %s (%ds/%ds) | Process: %.2fms",
                    lowFps == 0 ? "Loading..." : Integer.toString(lowFps),
                    LazyChunkLoading.getLowTimeWindowElapsedSeconds(),
                    LazyChunkLoading.getLowTimeWindowDurationSeconds(),
                    lazychunks$safeValue(LazyChunkLoading.getLastProcessingTimeMs())));

            if (config.adaptiveCoreBoostEnabled) {
                info.add(String.format(Locale.ROOT, "Core: +%d",
                        LazyChunkLoading.getAdaptiveCoreBoostLevel()));
            }

            StringBuilder status = new StringBuilder();
            if (LazyChunkLoading.wasThrottled()) {
                status.append(ChatFormatting.GREEN).append("Throttling");
            } else {
                status.append(ChatFormatting.GRAY).append("Idle");
            }

            if (lazychunks$safeValue(LazyChunkLoading.getQueueGrowthRate()) > 5) {
                status.append(ChatFormatting.GOLD).append(" [Queue+]");
            }

            if (TeleportDetector.isTeleportRecovery()) {
                status.append(ChatFormatting.RED).append(" [Teleport]");
            }

            info.add(status.toString());
        } else {
            info.add(ChatFormatting.GRAY + "Disabled");
        }

        info.add("");
        return List.copyOf(info);
    }

    @Unique
    private static double lazychunks$safeValue(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}
