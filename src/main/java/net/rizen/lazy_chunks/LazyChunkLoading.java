package net.rizen.lazy_chunks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import net.rizen.lazy_chunks.util.PacketRunnable;

import java.util.Arrays;
import java.util.Queue;

public class LazyChunkLoading {

    private static final double MIN_WEIGHT_THRESHOLD = 5.0;
    private static final double WEIGHT_CHUNK_WITH_LIGHT = 1.0;
    private static final double WEIGHT_LIGHT_UPDATE = 0.2;
    private static final double WEIGHT_FORGET_CHUNK = 2.6;
    private static final int FRAME_TIME_HISTORY_SIZE = 300;
    private static final int MIN_FRAME_TIME_SAMPLES = 60;
    private static final double ONE_PERCENT_LOW_SAMPLE_RATIO = 0.01;
    private static final long ONE_PERCENT_LOW_UPDATE_INTERVAL_NANOS = 500_000_000L;
    private static final double MIN_TRACKED_FRAME_TIME_MS = 0.1;
    private static final double MAX_TRACKED_FRAME_TIME_MS = 1000.0;
    private static final double LOW_TIME_FPS_THRESHOLD = 60.0;
    private static final long LOW_TIME_RESET_INTERVAL_NANOS = 12_000_000_000L;

    private static final double[] frameTimeHistory = new double[FRAME_TIME_HISTORY_SIZE];
    private static int frameTimeHistoryIndex = 0;
    private static int frameTimeHistoryCount = 0;
    private static long lowOnePercentWindowStartNanos = 0;
    private static double onePercentLowFps = 60.0;
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
    private static long lowOnePercentDisplayStartNanos = 0;
    private static long dynamicLowOnePercentStartNanos = 0;
    private static int dynamicExtraThrottleCores = 0;

    public static int getLastPendingTasks() { return lastPendingTasks; }
    public static double getLastWeight() { return lastWeight; }
    public static int getLastFps() { return lastFps; }
    public static double getLastBudget() { return lastBudget; }
    public static int getLastProcessed() { return lastProcessed; }
    public static boolean wasThrottled() { return lastThrottled; }
    public static double getQueueGrowthRate() { return queueGrowthRate; }
    public static double getLastProcessingTimeMs() { return lastProcessingTimeMs; }
    public static double getOnePercentLowFps() { return onePercentLowFps; }
    public static int getDynamicExtraThrottleCores() { return dynamicExtraThrottleCores; }
    public static double getLowOnePercentDurationSeconds() {
        long currentTimeNanos = System.nanoTime();
        if (lowOnePercentDisplayStartNanos == 0 || lowOnePercentWindowStartNanos == 0) {
            return 0.0;
        }

        if (currentTimeNanos - lowOnePercentWindowStartNanos >= LOW_TIME_RESET_INTERVAL_NANOS) {
            return 0.0;
        }

        long displayStartNanos = Math.max(lowOnePercentDisplayStartNanos, lowOnePercentWindowStartNanos);
        return Math.max(0.0, (currentTimeNanos - displayStartNanos) / 1_000_000_000.0);
    }

    private static void updateOnePercentLowFps(long currentTimeNanos) {
        if (frameTimeHistoryCount < MIN_FRAME_TIME_SAMPLES) {
            onePercentLowFps = getFallbackFps();
            lastOnePercentLowUpdateNanos = currentTimeNanos;
            return;
        }

        if (currentTimeNanos - lastOnePercentLowUpdateNanos < ONE_PERCENT_LOW_UPDATE_INTERVAL_NANOS) {
            return;
        }
        lastOnePercentLowUpdateNanos = currentTimeNanos;

        double[] sortedFrameTimes = Arrays.copyOf(frameTimeHistory, frameTimeHistoryCount);
        Arrays.sort(sortedFrameTimes);

        int slowFrameCount = Math.max(1, (int) Math.ceil(sortedFrameTimes.length * ONE_PERCENT_LOW_SAMPLE_RATIO));
        int firstSlowFrameIndex = sortedFrameTimes.length - slowFrameCount;
        double slowFrameTimeTotal = 0.0;

        for (int i = firstSlowFrameIndex; i < sortedFrameTimes.length; i++) {
            slowFrameTimeTotal += sortedFrameTimes[i];
        }

        double averageSlowFrameTimeMs = slowFrameTimeTotal / slowFrameCount;
        if (averageSlowFrameTimeMs > 0) {
            onePercentLowFps = 1000.0 / averageSlowFrameTimeMs;
        } else {
            onePercentLowFps = getFallbackFps();
        }
        updateLowOnePercentDisplayTimer(currentTimeNanos, true);
        updateDynamicThrottleCores(currentTimeNanos);
    }

    private static void updateLowOnePercentDisplayTimer(long currentTimeNanos, boolean hasEnoughSamples) {
        if (!hasEnoughSamples || onePercentLowFps >= LOW_TIME_FPS_THRESHOLD) {
            lowOnePercentDisplayStartNanos = 0;
            return;
        }

        if (lowOnePercentDisplayStartNanos == 0 || lowOnePercentDisplayStartNanos < lowOnePercentWindowStartNanos) {
            lowOnePercentDisplayStartNanos = currentTimeNanos;
        }
    }

    private static void updateDynamicThrottleCores(long currentTimeNanos) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        if (!isReadyToCheckVanillaAverageFps(currentTimeNanos, config)) {
            return;
        }

        // Vanilla FPS is intentionally read only after the 1% low preconditions pass.
        dynamicExtraThrottleCores = calculateDynamicExtraThrottleCores(config);
    }

    private static void resetDynamicThrottleCores() {
        dynamicExtraThrottleCores = 0;
    }

    private static boolean isReadyToCheckVanillaAverageFps(long currentTimeNanos, LazyChunksConfig config) {
        if (!config.dynamicThrottleCoreBoost || frameTimeHistoryCount < MIN_FRAME_TIME_SAMPLES) {
            dynamicLowOnePercentStartNanos = 0;
            resetDynamicThrottleCores();
            return false;
        }

        double lowFpsThreshold = Math.max(1.0, config.dynamicBoostOnePercentLowThreshold);
        if (onePercentLowFps >= lowFpsThreshold) {
            dynamicLowOnePercentStartNanos = 0;
            resetDynamicThrottleCores();
            return false;
        }

        if (dynamicLowOnePercentStartNanos == 0) {
            dynamicLowOnePercentStartNanos = currentTimeNanos;
            dynamicExtraThrottleCores = 0;
            return false;
        }

        long requiredLowDurationNanos = secondsToNanos(config.dynamicBoostLowDurationSeconds);
        if (currentTimeNanos - dynamicLowOnePercentStartNanos < requiredLowDurationNanos) {
            dynamicExtraThrottleCores = 0;
            return false;
        }

        return true;
    }

    private static long secondsToNanos(double seconds) {
        double clampedSeconds = Math.max(1.0, Math.min(60.0, seconds));
        return (long) (clampedSeconds * 1_000_000_000L);
    }

    private static int calculateDynamicExtraThrottleCores(LazyChunksConfig config) {
        int vanillaAverageFps = MinecraftClient.getInstance().getCurrentFps();
        int averageFpsThreshold = Math.max(1, config.dynamicBoostAverageFpsThreshold);
        
        int maxExtraCores = Math.max(0, Math.min(3, config.dynamicBoostMaxExtraCores));
        if (maxExtraCores == 0) {
            return 0;
        }
        
        // Only add cores when average FPS is ABOVE the threshold.
        // If FPS <= threshold (e.g., 60 FPS with default 120 threshold), cores will DECREASE.
        // This prevents adding cores when the system is already struggling.
        if (vanillaAverageFps <= averageFpsThreshold) {
            int decreaseStep = Math.max(1, config.dynamicBoostCoreDecreaseStep);
            return Math.max(0, dynamicExtraThrottleCores - decreaseStep);
        }

        int fpsPerExtraCore = Math.max(1, config.dynamicBoostFpsPerExtraCore);
        int targetExtraCores = 1 + (vanillaAverageFps - averageFpsThreshold) / fpsPerExtraCore;
        targetExtraCores = Math.max(1, Math.min(maxExtraCores, targetExtraCores));

        int increaseStep = Math.max(1, config.dynamicBoostCoreIncreaseStep);
        return Math.min(targetExtraCores, dynamicExtraThrottleCores + increaseStep);
    }

    public static void recordFrameTime(double frameTimeMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0) {
            return;
        }

        long currentTimeNanos = System.nanoTime();
        resetLowOnePercentWindowIfNeeded(currentTimeNanos);

        frameTimeMs = Math.max(MIN_TRACKED_FRAME_TIME_MS, Math.min(MAX_TRACKED_FRAME_TIME_MS, frameTimeMs));
        frameTimeHistory[frameTimeHistoryIndex] = frameTimeMs;
        frameTimeHistoryIndex = (frameTimeHistoryIndex + 1) % FRAME_TIME_HISTORY_SIZE;
        if (frameTimeHistoryCount < FRAME_TIME_HISTORY_SIZE) {
            frameTimeHistoryCount++;
        }

        updateOnePercentLowFps(currentTimeNanos);
    }

    private static void resetLowOnePercentWindowIfNeeded(long currentTimeNanos) {
        if (lowOnePercentWindowStartNanos == 0) {
            lowOnePercentWindowStartNanos = currentTimeNanos;
            return;
        }

        if (currentTimeNanos - lowOnePercentWindowStartNanos < LOW_TIME_RESET_INTERVAL_NANOS) {
            return;
        }

        lowOnePercentWindowStartNanos = currentTimeNanos;
        lowOnePercentDisplayStartNanos = 0;
        dynamicLowOnePercentStartNanos = 0;
        resetDynamicThrottleCores();
    }

    private static double getFallbackFps() {
        int gameFps = MinecraftClient.getInstance().getCurrentFps();
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
        int gameFps = MinecraftClient.getInstance().getCurrentFps();
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
        int instantFps = MinecraftClient.getInstance().getCurrentFps();

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

            if (packet instanceof ChunkDataS2CPacket) {
                return WEIGHT_CHUNK_WITH_LIGHT;
            }

            if (packet instanceof LightUpdateS2CPacket) {
                return WEIGHT_LIGHT_UPDATE;
            }

            if (packet instanceof UnloadChunkS2CPacket) {
                return WEIGHT_FORGET_CHUNK;
            }
        }

        return 0.0;
    }
}
