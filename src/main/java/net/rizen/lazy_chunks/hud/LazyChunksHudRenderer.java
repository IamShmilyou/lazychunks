package net.rizen.lazy_chunks.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.Identifier;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.TeleportDetector;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LazyChunksHudRenderer {

    private static final Object LOCK = new Object();
    private static volatile long lastUpdateTimeNanos = 0L;
    // Keep HUD updates slow enough that debug numbers do not flicker.
    private static final long UPDATE_INTERVAL_NANOS = 2_000_000_000L;
    private static volatile List<String> cachedDebugLines = null;
    private static final Minecraft minecraft = Minecraft.getInstance();

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(LazyChunksMod.MOD_ID, "chunk_debug_hud"),
                LazyChunksHudRenderer::extractRenderState
        );
        LazyChunksMod.LOGGER.info("LazyChunks HUD renderer registered (26.1.2 compatible)");
    }

    private static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (minecraft.options.hideGui || !minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        LazyChunksConfig config = LazyChunksConfig.getInstance();
        if (!config.lazyChunkLoadingEnabled || !config.debugInfoEnabled) {
            return;
        }

        List<String> lines = getDebugLines();
        if (lines.isEmpty()) {
            return;
        }

        Matrix3x2fStack matrices = graphics.pose();
        matrices.pushMatrix();

        try {
            // 最终完美位置：Iris信息正下方，Debug charts正上方
            int x = 5;
            int y = 220;
            int lineHeight = minecraft.font.lineHeight + 2;

            for (String line : lines) {
                if (line == null || line.isEmpty()) continue;
                graphics.text(
                        minecraft.font,
                        line,
                        x,
                        y,
                        0xFFFFFFFF,
                        true
                );
                y += lineHeight;
            }
        } finally {
            matrices.popMatrix();
        }
    }

    private static List<String> getDebugLines() {
        long now = System.nanoTime();
        List<String> cached = cachedDebugLines;

        if (cached == null || now - lastUpdateTimeNanos >= UPDATE_INTERVAL_NANOS) {
            synchronized (LOCK) {
                if (cached == null || now - lastUpdateTimeNanos >= UPDATE_INTERVAL_NANOS) {
                    cached = buildDebugInfo();
                    cachedDebugLines = cached;
                    lastUpdateTimeNanos = now;
                }
            }
        }
        return cached;
    }

    private static List<String> buildDebugInfo() {
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        List<String> info = new ArrayList<>();
        if (!config.debugInfoEnabled) {
            return List.of();
        }

        info.add(ChatFormatting.YELLOW + ChatFormatting.BOLD.toString() + "LazyChunks " + LazyChunksMod.VERSION);

        if (config.lazyChunkLoadingEnabled) {
            info.add(String.format(Locale.ROOT,
                    "Pending: %d | Weight: %.1f | Budget: %.1f",
                    LazyChunkLoading.getLastPendingTasks(),
                    safeFloatValue(LazyChunkLoading.getLastWeight()),
                    safeFloatValue(LazyChunkLoading.getLastBudget())));

            int lowFps = LazyChunkLoading.getOnePercentLowFps();
            info.add(String.format(Locale.ROOT,
                    "1%% Low FPS: %s (%ds/%ds) | Process: %.2fms",
                    lowFps == 0 ? "Loading..." : Integer.toString(lowFps),
                    LazyChunkLoading.getLowTimeWindowElapsedSeconds(),
                    LazyChunkLoading.getLowTimeWindowDurationSeconds(),
                    safeFloatValue(LazyChunkLoading.getLastProcessingTimeMs())));

            StringBuilder status = new StringBuilder();
            if (LazyChunkLoading.wasThrottled()) {
                status.append(ChatFormatting.GREEN).append("Throttling");
            } else {
                status.append(ChatFormatting.GRAY).append("Idle");
            }

            if (safeFloatValue(LazyChunkLoading.getQueueGrowthRate()) > 5) {
                status.append(" ").append(ChatFormatting.GOLD).append("[Queue+]");
            }

            if (TeleportDetector.isTeleportRecovery()) {
                status.append(" ").append(ChatFormatting.RED).append("[Teleport]");
            }

            info.add(status.toString());
        } else {
            info.add(ChatFormatting.GRAY + "Disabled");
        }

        return List.copyOf(info);
    }

    private static double safeFloatValue(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) || value < 0 ? 0.0 : value;
    }

    private static double safeFloatValue(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) || value < 0 ? 0.0 : value;
    }
}
