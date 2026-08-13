package net.rizen.lazy_chunks.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.TeleportDetector;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = LazyChunksMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LazyChunksDebugOverlay {
    private static final long UPDATE_INTERVAL_MS = 1500;
    private static final long MAX_FRAME_SAMPLE_NANOS = 1_000_000_000L;

    private static long lastFrameSampleNanos = 0;
    private static long lastUpdateTime = 0;
    private static int cachedPending = 0;
    private static double cachedWeight = 0;
    private static double cachedBudget = 0;
    private static double cachedProcessingTime = 0;
    private static double cachedQueueGrowth = 0;
    private static boolean cachedTeleportRecovery = false;
    private static double cachedOnePercentLowFps = 0;
    private static int cachedExtraThrottleCores = 0;
    private static double cachedOnePercentLowWindowDuration = 0;

    private LazyChunksDebugOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        long now = System.nanoTime();
        if (lastFrameSampleNanos > 0) {
            long elapsedNanos = now - lastFrameSampleNanos;
            if (elapsedNanos > 0 && elapsedNanos <= MAX_FRAME_SAMPLE_NANOS) {
                LazyChunkLoading.recordFrameTime(elapsedNanos / 1_000_000.0);
            }
        }

        lastFrameSampleNanos = now;
        TeleportDetector.tick();
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null || !minecraft.options.renderDebug) {
            return;
        }

        LazyChunksConfig config = LazyChunksConfig.getInstance();
        if (!config.showDebugOverlay) {
            return;
        }

        updateCache();

        List<String> left = event.getLeft();
        int insertIndex = 0;
        left.add(insertIndex++, ChatFormatting.YELLOW.toString() + ChatFormatting.BOLD + "LazyChunks " + LazyChunksMod.VERSION);

        if (config.lazyChunkLoadingEnabled) {
            left.add(insertIndex++, String.format(Locale.ROOT, "Pending: %d | Weight: %.1f | Budget: %.1f",
                    cachedPending,
                    cachedWeight,
                    cachedBudget));

            left.add(insertIndex++, String.format(Locale.ROOT, "1%% Low: %d | Core+: %d",
                    Math.round(cachedOnePercentLowFps),
                    cachedExtraThrottleCores));

            left.add(insertIndex++, String.format(Locale.ROOT, "Process: %.2fms | Low Time: %ds",
                    cachedProcessingTime,
                    (int) Math.floor(cachedOnePercentLowWindowDuration)));

            left.add(insertIndex++, getStatusLine());
        } else {
            left.add(insertIndex++, ChatFormatting.GRAY + "Disabled");
        }

        left.add(insertIndex, "");
    }

    private static void updateCache() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime <= UPDATE_INTERVAL_MS) {
            return;
        }

        lastUpdateTime = now;
        cachedPending = LazyChunkLoading.getLastPendingTasks();
        cachedWeight = LazyChunkLoading.getLastWeight();
        cachedBudget = LazyChunkLoading.getLastBudget();
        cachedProcessingTime = LazyChunkLoading.getLastProcessingTimeMs();
        cachedQueueGrowth = LazyChunkLoading.getQueueGrowthRate();
        cachedTeleportRecovery = TeleportDetector.isTeleportRecovery();
        cachedOnePercentLowFps = LazyChunkLoading.getOnePercentLowFps();
        cachedExtraThrottleCores = LazyChunkLoading.getDynamicExtraThrottleCores();
        cachedOnePercentLowWindowDuration = LazyChunkLoading.getOnePercentLowWindowDurationSeconds();
    }

    private static String getStatusLine() {
        StringBuilder status = new StringBuilder();
        if (LazyChunkLoading.wasThrottled()) {
            status.append(ChatFormatting.GREEN).append("Throttling");
        } else {
            status.append(ChatFormatting.GRAY).append("Idle");
        }

        if (cachedQueueGrowth > 5) {
            status.append(ChatFormatting.GOLD).append(" [Queue+]");
        }

        if (cachedExtraThrottleCores > 0) {
            status.append(ChatFormatting.AQUA).append(" [Core+").append(cachedExtraThrottleCores).append("]");
        }

        if (cachedTeleportRecovery) {
            status.append(ChatFormatting.RED).append(" [Teleport]");
        }

        return status.toString();
    }
}
