package net.rizen.lazy_chunks;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import net.rizen.lazy_chunks.util.PacketRunnable;

import java.util.Queue;

public class LazyChunkLoading {

    private static final double MIN_WEIGHT_THRESHOLD = 5.0;
    private static final double WEIGHT_CHUNK_WITH_LIGHT = 1.0;
    private static final double WEIGHT_LIGHT_UPDATE = 0.2;
    private static final double WEIGHT_FORGET_CHUNK = 2.6;
    private static final int FRAME_TIME_HISTORY_SIZE = 300;
    private static final int MIN_ONE_PERCENT_LOW_SAMPLES = 60;
    private static final int MIN_DYNAMIC_CORE_SAMPLES = 120;
    private static final double LOW_TIME_DISPLAY_FPS_THRESHOLD = 60.0;
    private static final long LOW_TIME_DISPLAY_WINDOW_NANOS = 12_000_000_000L;
    private static final long ONE_PERCENT_LOW_UPDATE_INTERVAL_NANOS = 1_000_000_000L;
    private static final double ONE_PERCENT_LOW_DROP_ALPHA = 0.45;
    private static final double ONE_PERCENT_LOW_RECOVERY_ALPHA = 0.35;
    private static final double ONE_PERCENT_LOW_SAMPLE_RATIO = 0.01;
    private static final int MIN_ONE_PERCENT_LOW_RANK = 3;
    private static final int ONE_PERCENT_LOW_RANK = Math.max(
            MIN_ONE_PERCENT_LOW_RANK,
            (int) Math.ceil(FRAME_TIME_HISTORY_SIZE * ONE_PERCENT_LOW_SAMPLE_RATIO)
    );
    private static final double ONE_PERCENT_LOW_SPIKE_CAP_RATIO = 1.8;
    private static final double ONE_PERCENT_LOW_SPIKE_CAP_GAP_MS = 25.0;
    private static final double MIN_TRACKED_FRAME_TIME_MS = 0.1;
    private static final double MAX_TRACKED_FRAME_TIME_MS = 250.0;

    private static final double[] frameTimeHistory = new double[FRAME_TIME_HISTORY_SIZE];
    private static final double[] slowestFrameTimesScratch = new double[ONE_PERCENT_LOW_RANK];
    private static int frameTimeHistoryIndex = 0;
    private static int frameTimeHistoryCount = 0;
    private static long lowTimeDisplayWindowStartNanos = 0;
    private static double onePercentLowFps = 60.0;
    private static boolean onePercentLowInitialized = false;
    private static long lastOnePercentLowUpdateNanos = 0;
    private static int previousQueueDepth = 0;
    private static double queueGrowthRate = 0.0;
    private static double lastProcessingTimeMs = 0.0;
    private static int lastPendingTasks = 0;
    private static double lastWeight = 0;
    private static int lastFps = 0;
    private static double lastBudget = 0;
    private static int lastProcessed = 0;
    private static boolean lastThrottled = false;
    private static long lowOnePercentStartNanos = 0;
    private static int dynamicExtraThrottleCores = 0;

    public static int getLastPendingTasks() { return lastPendingTasks; }
    public static double getLastWeight() { return lastWeight; }
    public static int getLastFps() { return lastFps; }
    public static double getLastBudget() { return lastBudget; }
    public static int getLastProcessed() { return lastProcessed; }
    public static boolean wasThrottled() { return lastThrottled; }
    public static double getQueueGrowthRate() { return queueGrowthRate; }
    public static double getLastProcessingTimeMs() { return lastProcessingTimeMs; }
    public static double getOnePercentLowFps() {
        return frameTimeHistoryCount == 0 ? getFallbackFps() : onePercentLowFps;
    }
    public static int getDynamicExtraThrottleCores() { return dynamicExtraThrottleCores; }
    public static int getVanillaFpsForDynamicBoost() {
        return Math.max(0, Minecraft.getInstance().getFps());
    }
    public static double getLowOnePercentDurationSeconds() {
        if (lowOnePercentStartNanos == 0) {
            return 0.0;
        }

        return (System.nanoTime() - lowOnePercentStartNanos) / 1_000_000_000.0;
    }
    public static double getOnePercentLowWindowDurationSeconds() {
        if (lowTimeDisplayWindowStartNanos == 0 || frameTimeHistoryCount == 0 || onePercentLowFps >= LOW_TIME_DISPLAY_FPS_THRESHOLD) {
            return 0.0;
        }

        long elapsedNanos = System.nanoTime() - lowTimeDisplayWindowStartNanos;
        return Math.min(elapsedNanos, LOW_TIME_DISPLAY_WINDOW_NANOS) / 1_000_000_000.0;
    }

    private static void updateOnePercentLowFps(long currentTimeNanos) {
        if (onePercentLowInitialized && currentTimeNanos - lastOnePercentLowUpdateNanos < ONE_PERCENT_LOW_UPDATE_INTERVAL_NANOS) {
            return;
        }

        lastOnePercentLowUpdateNanos = currentTimeNanos;
        double targetFps = frameTimeHistoryCount < MIN_ONE_PERCENT_LOW_SAMPLES
                ? getFallbackFps()
                : calculateOnePercentLowFps();

        if (!Double.isFinite(targetFps) || targetFps <= 0) {
            targetFps = getFallbackFps();
        }

        onePercentLowFps = smoothOnePercentLowFps(targetFps);
        onePercentLowInitialized = true;
        updateDynamicThrottleCores(currentTimeNanos);
    }

    private static double smoothOnePercentLowFps(double targetFps) {
        targetFps = Math.max(1.0, targetFps);

        if (!onePercentLowInitialized) {
            return targetFps;
        }

        double alpha = targetFps < onePercentLowFps
                ? ONE_PERCENT_LOW_DROP_ALPHA
                : ONE_PERCENT_LOW_RECOVERY_ALPHA;
        return Math.max(1.0, onePercentLowFps + (targetFps - onePercentLowFps) * alpha);
    }

    private static double calculateOnePercentLowFps() {
        if (frameTimeHistoryCount == 0) {
            return getFallbackFps();
        }

        resetSlowestFrameTimesScratch();

        for (int i = 0; i < frameTimeHistoryCount; i++) {
            insertSlowFrameTime(frameTimeHistory[i]);
        }

        double slowFrameTimeTotal = 0.0;
        int slowFrameCount = 0;

        for (int i = 0; i < slowestFrameTimesScratch.length; i++) {
            if (slowestFrameTimesScratch[i] <= 0) {
                continue;
            }

            double frameTimeMs = i == 0
                    ? getCappedWorstFrameTime(slowestFrameTimesScratch[0], slowestFrameTimesScratch.length > 1 ? slowestFrameTimesScratch[1] : 0.0)
                    : slowestFrameTimesScratch[i];
            slowFrameTimeTotal += frameTimeMs;
            slowFrameCount++;
        }

        if (slowFrameCount > 0) {
            return 1000.0 / (slowFrameTimeTotal / slowFrameCount);
        }

        return getFallbackFps();
    }

    private static double getCappedWorstFrameTime(double worstFrameTimeMs, double secondWorstFrameTimeMs) {
        if (secondWorstFrameTimeMs <= 0) {
            return worstFrameTimeMs;
        }

        boolean extremeSpike = worstFrameTimeMs - secondWorstFrameTimeMs >= ONE_PERCENT_LOW_SPIKE_CAP_GAP_MS
                && worstFrameTimeMs / secondWorstFrameTimeMs >= ONE_PERCENT_LOW_SPIKE_CAP_RATIO;
        if (!extremeSpike) {
            return worstFrameTimeMs;
        }

        double ratioCap = secondWorstFrameTimeMs * ONE_PERCENT_LOW_SPIKE_CAP_RATIO;
        double gapCap = secondWorstFrameTimeMs + ONE_PERCENT_LOW_SPIKE_CAP_GAP_MS;
        return Math.max(ratioCap, gapCap);
    }

    private static void resetSlowestFrameTimesScratch() {
        for (int i = 0; i < slowestFrameTimesScratch.length; i++) {
            slowestFrameTimesScratch[i] = 0.0;
        }
    }

    private static void insertSlowFrameTime(double frameTimeMs) {
        for (int slot = 0; slot < slowestFrameTimesScratch.length; slot++) {
            if (frameTimeMs <= slowestFrameTimesScratch[slot]) {
                continue;
            }

            for (int move = slowestFrameTimesScratch.length - 1; move > slot; move--) {
                slowestFrameTimesScratch[move] = slowestFrameTimesScratch[move - 1];
            }

            slowestFrameTimesScratch[slot] = frameTimeMs;
            return;
        }
    }

    private static void updateDynamicThrottleCores(long currentTimeNanos) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        if (!config.dynamicThrottleCoreBoost || frameTimeHistoryCount < MIN_DYNAMIC_CORE_SAMPLES) {
            resetDynamicThrottleCores();
            return;
        }

        double lowFpsThreshold = Math.max(1.0, config.dynamicBoostOnePercentLowThreshold);
        if (onePercentLowFps >= lowFpsThreshold) {
            lowOnePercentStartNanos = 0;
            resetDynamicThrottleCores();
            return;
        }

        if (lowOnePercentStartNanos == 0) {
            lowOnePercentStartNanos = currentTimeNanos;
            dynamicExtraThrottleCores = 0;
            return;
        }

        long requiredLowDurationNanos = secondsToNanos(config.dynamicBoostLowDurationSeconds);
        if (currentTimeNanos - lowOnePercentStartNanos < requiredLowDurationNanos) {
            dynamicExtraThrottleCores = 0;
            return;
        }

        dynamicExtraThrottleCores = calculateDynamicExtraThrottleCores(getVanillaFpsForDynamicBoost(), config);
    }

    private static void resetDynamicThrottleCores() {
        dynamicExtraThrottleCores = 0;
    }

    private static long secondsToNanos(double seconds) {
        double clampedSeconds = Math.max(1.0, Math.min(60.0, seconds));
        return (long) (clampedSeconds * 1_000_000_000L);
    }

    private static int calculateDynamicExtraThrottleCores(int vanillaAverageFps, LazyChunksConfig config) {
        int averageFpsThreshold = Math.max(1, config.dynamicBoostAverageFpsThreshold);
        if (vanillaAverageFps <= averageFpsThreshold) {
            int decreaseStep = Math.max(1, config.dynamicBoostCoreDecreaseStep);
            return Math.max(0, dynamicExtraThrottleCores - decreaseStep);
        }

        int maxExtraCores = Math.max(0, Math.min(3, config.dynamicBoostMaxExtraCores));
        if (maxExtraCores == 0) {
            return 0;
        }

        int fpsPerExtraCore = Math.max(1, config.dynamicBoostFpsPerExtraCore);
        int extraCores = 1 + (vanillaAverageFps - averageFpsThreshold) / fpsPerExtraCore;
        return Math.max(1, Math.min(maxExtraCores, extraCores));
    }

    public static void recordFrameTime(double frameTimeMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0) {
            return;
        }

        long currentTimeNanos = System.nanoTime();

        frameTimeMs = Math.max(MIN_TRACKED_FRAME_TIME_MS, Math.min(MAX_TRACKED_FRAME_TIME_MS, frameTimeMs));
        frameTimeHistory[frameTimeHistoryIndex] = frameTimeMs;
        frameTimeHistoryIndex = (frameTimeHistoryIndex + 1) % FRAME_TIME_HISTORY_SIZE;
        if (frameTimeHistoryCount < FRAME_TIME_HISTORY_SIZE) {
            frameTimeHistoryCount++;
        }

        updateOnePercentLowFps(currentTimeNanos);
        updateLowTimeDisplayWindow(currentTimeNanos);
    }

    private static void updateLowTimeDisplayWindow(long currentTimeNanos) {
        if (onePercentLowFps >= LOW_TIME_DISPLAY_FPS_THRESHOLD) {
            lowTimeDisplayWindowStartNanos = 0;
            return;
        }

        if (lowTimeDisplayWindowStartNanos == 0) {
            lowTimeDisplayWindowStartNanos = currentTimeNanos;
            return;
        }

        if (currentTimeNanos - lowTimeDisplayWindowStartNanos < LOW_TIME_DISPLAY_WINDOW_NANOS) {
            return;
        }

        lowTimeDisplayWindowStartNanos = currentTimeNanos;
    }

    private static double getFallbackFps() {
        int gameFps = Minecraft.getInstance().getFps();
        return gameFps > 0 ? gameFps : onePercentLowFps;
    }

    public static void recordProcessingTime(double timeMs) {
        lastProcessingTimeMs = timeMs;
    }

    private static void updateQueueMetrics(int currentDepth) {
        queueGrowthRate = queueGrowthRate * 0.8 + (currentDepth - previousQueueDepth) * 0.2;
        previousQueueDepth = currentDepth;
    }

    private static double getStabilityMultiplier() {
        int gameFps = Minecraft.getInstance().getFps();
        if (gameFps <= 0) {
            return 1.0;
        }

        double stabilityFactor = 1.0 / (1.0 + (gameFps - onePercentLowFps) / gameFps);
        return Math.max(0.5, Math.min(1.0, stabilityFactor));
    }

    private static double getQueueGrowthMultiplier() {
        if (queueGrowthRate > 10) {
            return 0.5;
        } else if (queueGrowthRate > 5) {
            return 0.7;
        } else if (queueGrowthRate < -5) {
            return 1.2;
        }
        return 1.0;
    }

    public static long getMaxProcessingTimeNanos() {
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        double targetFrameTimeMs = 1000.0 / Math.max(config.targetFps, 1);
        double maxTimeMs = targetFrameTimeMs * (config.maxFrameTimePercent / 100.0);
        return (long) (maxTimeMs * 1_000_000);
    }

    public static int getTaskCount(Queue<? extends Runnable> pendingTasks) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        if (!config.lazyChunkLoadingEnabled) {
            lastThrottled = false;
            return Integer.MAX_VALUE;
        }

        if (pendingTasks.isEmpty()) {
            lastPendingTasks = 0;
            lastWeight = 0;
            lastThrottled = false;
            return 0;
        }

        Runnable[] tasks = pendingTasks.toArray(new Runnable[0]);
        double totalWeight = getTotalChunkWeight(tasks);
        int taskCount = tasks.length;
        int instantFps = Minecraft.getInstance().getFps();

        updateQueueMetrics(taskCount);

        lastPendingTasks = taskCount;
        lastWeight = totalWeight;
        lastFps = instantFps;

        if (totalWeight < MIN_WEIGHT_THRESHOLD) {
            lastThrottled = false;
            lastBudget = totalWeight;
            lastProcessed = taskCount;
            return Integer.MAX_VALUE;
        }

        if (onePercentLowFps >= config.fpsThreshold) {
            lastThrottled = false;
            lastBudget = totalWeight;
            lastProcessed = taskCount;
            return Integer.MAX_VALUE;
        }

        double maxWeight = config.baseWeightPerFrame * onePercentLowFps / config.targetFps;

        if (config.proactiveThrottling) {
            maxWeight *= getStabilityMultiplier();
            maxWeight *= getQueueGrowthMultiplier();
        }

        if (config.teleportProtection) {
            maxWeight *= TeleportDetector.getBudgetMultiplier();
        }

        maxWeight += getDynamicThrottleCoreBudget(config);
        maxWeight = Math.max(maxWeight, config.minimumBudget);

        int limit = getCountForWeight(tasks, maxWeight);

        lastThrottled = true;
        lastBudget = maxWeight;
        lastProcessed = limit;

        return limit;
    }

    private static double getDynamicThrottleCoreBudget(LazyChunksConfig config) {
        if (!config.dynamicThrottleCoreBoost || dynamicExtraThrottleCores <= 0) {
            return 0.0;
        }

        return dynamicExtraThrottleCores * Math.max(0.0, config.dynamicBoostWeightPerCore);
    }

    private static int getCountForWeight(Runnable[] tasks, double maxWeight) {
        double currentWeight = 0.0;

        for (int i = 0; i < tasks.length; i++) {
            double taskWeight = getChunkUpdateWeight(tasks[i]);

            if (currentWeight + taskWeight > maxWeight && i > 0) {
                return i;
            }

            currentWeight += taskWeight;
        }

        return tasks.length;
    }

    private static double getTotalChunkWeight(Runnable[] tasks) {
        double weight = 0.0;

        for (Runnable task : tasks) {
            weight += getChunkUpdateWeight(task);
        }

        return weight;
    }

    private static double getChunkUpdateWeight(Runnable task) {
        if (task instanceof PacketRunnable packetRunnable) {
            Packet<?> packet = packetRunnable.getPacket();

            if (packet instanceof ClientboundLevelChunkWithLightPacket) {
                return WEIGHT_CHUNK_WITH_LIGHT;
            }

            if (packet instanceof ClientboundLightUpdatePacket) {
                return WEIGHT_LIGHT_UPDATE;
            }

            if (packet instanceof ClientboundForgetLevelChunkPacket) {
                return WEIGHT_FORGET_CHUNK;
            }
        }

        return 0.0;
    }
}
