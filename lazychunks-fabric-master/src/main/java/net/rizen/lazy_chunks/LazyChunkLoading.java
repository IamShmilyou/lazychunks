package net.rizen.lazy_chunks;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import net.rizen.lazy_chunks.util.PacketRunnable;

import java.util.Arrays;
import java.util.Queue;

public class LazyChunkLoading {

    private static final double MIN_WEIGHT_THRESHOLD = 5.0;
    private static final double WEIGHT_CHUNK_WITH_LIGHT = 1.0;
    private static final double WEIGHT_LIGHT_UPDATE = 0.2;
    private static final double WEIGHT_FORGET_CHUNK = 2.6;
    private static final double MICROS_PER_SECOND = 1_000_000.0;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int FRAME_TIME_HISTORY_SIZE = 4096;
    private static final long FRAME_TIME_HISTORY_WINDOW_NANOS = 12L * NANOS_PER_SECOND;
    private static final int MIN_FRAME_TIME_SAMPLES = 120;
    private static final double ONE_PERCENT_LOW_SAMPLE_RATIO = 0.01;
    private static final long ONE_PERCENT_LOW_UPDATE_INTERVAL_NANOS = 500_000_000L;
    private static final int MIN_TRACKED_FRAME_TIME_MICROS = 100;
    private static final double LOW_TIME_FPS_THRESHOLD = 60.0;
    private static final long LOW_ONE_PERCENT_DURATION_RESET_INTERVAL_NANOS = FRAME_TIME_HISTORY_WINDOW_NANOS;
    private static final Object FRAME_TIME_HISTORY_LOCK = new Object();
    private static final int[] frameTimeHistoryMicros = new int[FRAME_TIME_HISTORY_SIZE];
    private static final long[] frameTimeHistoryNanos = new long[FRAME_TIME_HISTORY_SIZE];
    private static int frameTimeHistoryIndex = 0;
    private static int frameTimeHistoryCount = 0;
    private static volatile double onePercentLowFps = 0.0;
    private static volatile boolean onePercentLowInitialized = false;
    private static long lastOnePercentLowUpdateNanos = 0;
    private static long lowTimeStartNanos = 0;
    private static long lowOnePercentStartNanos = 0;
    private static long lastAdaptiveCoreChangeNanos = 0;
    private static volatile double lowTimeDurationSeconds = 0.0;
    private static volatile double adaptiveLowDurationSeconds = 0.0;
    private static volatile int adaptiveExtraCores = 0;
    private static boolean adaptiveBoostActivated = false;
    private static long adaptiveBoostActivationTimeNanos = 0;
    private static int previousQueueDepth = 0;
    private static double queueGrowthRate = 0.0;
    private static double lastProcessingTimeMs = 0.0;
    private static int lastPendingTasks = 0;
    private static double lastWeight = 0;
    private static int lastFps = 0;
    private static double lastBudget = 0;
    private static int lastProcessed = 0;
    private static boolean lastThrottled = false;

    public static int getLastPendingTasks() { return lastPendingTasks; }
    public static double getLastWeight() { return lastWeight; }
    public static int getLastFps() { return lastFps; }
    public static double getLastBudget() { return lastBudget; }
    public static int getLastProcessed() { return lastProcessed; }
    public static boolean wasThrottled() { return lastThrottled; }
    public static double getQueueGrowthRate() { return queueGrowthRate; }
    public static double getLastProcessingTimeMs() { return lastProcessingTimeMs; }
    public static double getOnePercentLowFps() { return onePercentLowFps; }
    public static int getAdaptiveExtraCores() { return adaptiveExtraCores; }
    public static double getLowTimeDurationSeconds() { return lowTimeDurationSeconds; }
    public static double getAdaptiveLowDurationSeconds() { return adaptiveLowDurationSeconds; }

    private static void updateOnePercentLowFps(long currentTimeNanos) {
        double measuredFps;

        synchronized (FRAME_TIME_HISTORY_LOCK) {
            if (lastOnePercentLowUpdateNanos != 0
                    && currentTimeNanos - lastOnePercentLowUpdateNanos < ONE_PERCENT_LOW_UPDATE_INTERVAL_NANOS) {
                return;
            }

            lastOnePercentLowUpdateNanos = currentTimeNanos;
            measuredFps = calculateOnePercentLowFpsLocked(currentTimeNanos);
        }

        if (measuredFps > 0) {
            updateOnePercentLowFpsValue(measuredFps);
        } else {
            resetOnePercentLowFpsValue();
        }
    }

    private static double calculateOnePercentLowFpsLocked(long currentTimeNanos) {
        if (frameTimeHistoryCount < MIN_FRAME_TIME_SAMPLES) {
            return 0.0;
        }

        int[] sortedFrameTimeMicros = new int[Math.min(frameTimeHistoryCount, frameTimeHistoryMicros.length)];
        int validSamples = 0;
        long oldestTrackedSampleNanos = currentTimeNanos - FRAME_TIME_HISTORY_WINDOW_NANOS;

        for (int i = 0; i < frameTimeHistoryMicros.length; i++) {
            int frameTimeMicros = frameTimeHistoryMicros[i];
            long frameTimeNanos = frameTimeHistoryNanos[i];
            if (frameTimeMicros > 0 && frameTimeNanos >= oldestTrackedSampleNanos) {
                sortedFrameTimeMicros[validSamples++] = frameTimeMicros;
            }
        }

        if (validSamples < MIN_FRAME_TIME_SAMPLES) {
            return 0.0;
        }

        Arrays.sort(sortedFrameTimeMicros, 0, validSamples);

        int slowFrameCount = Math.max(1, (int) Math.ceil(validSamples * ONE_PERCENT_LOW_SAMPLE_RATIO));
        int slowFrameStartIndex = validSamples - slowFrameCount;
        long slowFrameTimeTotalMicros = 0;
        for (int i = slowFrameStartIndex; i < validSamples; i++) {
            slowFrameTimeTotalMicros += sortedFrameTimeMicros[i];
        }

        double averageSlowFrameTimeMicros = slowFrameTimeTotalMicros / (double) slowFrameCount;
        return averageSlowFrameTimeMicros > 0.0 ? MICROS_PER_SECOND / averageSlowFrameTimeMicros : 0.0;
    }

    private static void updateOnePercentLowFpsValue(double measuredFps) {
        if (!Double.isFinite(measuredFps) || measuredFps <= 0) {
            return;
        }

        onePercentLowFps = measuredFps;
        onePercentLowInitialized = true;
    }

    private static void resetOnePercentLowFpsValue() {
        onePercentLowFps = 0.0;
        onePercentLowInitialized = false;
    }

    public static void recordFrameTime(double frameTimeMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0) {
            return;
        }

        int frameTimeMicros = clampFrameTimeMicros(frameTimeMs);
        long currentTimeNanos = System.nanoTime();
        int frameTimeSampleCount;

        synchronized (FRAME_TIME_HISTORY_LOCK) {
            frameTimeHistoryMicros[frameTimeHistoryIndex] = frameTimeMicros;
            frameTimeHistoryNanos[frameTimeHistoryIndex] = currentTimeNanos;

            frameTimeHistoryIndex = (frameTimeHistoryIndex + 1) % FRAME_TIME_HISTORY_SIZE;
            if (frameTimeHistoryCount < FRAME_TIME_HISTORY_SIZE) {
                frameTimeHistoryCount++;
            }

            frameTimeSampleCount = getTrackedFrameTimeSampleCountLocked(currentTimeNanos);
        }

        updateOnePercentLowFps(currentTimeNanos);
        updateLowTimeState(currentTimeNanos, frameTimeSampleCount);
        updateLowOnePercentDurationState(LazyChunksConfig.getInstance(), currentTimeNanos, frameTimeSampleCount);
    }

    private static int getTrackedFrameTimeSampleCountLocked(long currentTimeNanos) {
        long oldestTrackedSampleNanos = currentTimeNanos - FRAME_TIME_HISTORY_WINDOW_NANOS;
        int count = 0;

        for (long frameTimeNanos : frameTimeHistoryNanos) {
            if (frameTimeNanos != 0 && frameTimeNanos >= oldestTrackedSampleNanos) {
                count++;
            }
        }

        return count;
    }

    private static int clampFrameTimeMicros(double frameTimeMs) {
        double frameTimeMicros = frameTimeMs * 1000.0;
        if (frameTimeMicros <= MIN_TRACKED_FRAME_TIME_MICROS) {
            return MIN_TRACKED_FRAME_TIME_MICROS;
        }

        if (frameTimeMicros >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) Math.round(frameTimeMicros);
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
        if (gameFps <= 0) return 1.0;
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

    private static void updateLowTimeState(long currentTimeNanos, int frameTimeSampleCount) {
        if (!onePercentLowInitialized
                || frameTimeSampleCount < MIN_FRAME_TIME_SAMPLES
                || onePercentLowFps >= LOW_TIME_FPS_THRESHOLD) {
            resetLowTimeState();
            return;
        }

        if (lowTimeStartNanos == 0) {
            lowTimeStartNanos = currentTimeNanos;
        }

        long lowTimeNanos = currentTimeNanos - lowTimeStartNanos;
        if (lowTimeNanos >= LOW_ONE_PERCENT_DURATION_RESET_INTERVAL_NANOS) {
            resetLowTimeState();
            lowTimeStartNanos = currentTimeNanos;
            return;
        }

        lowTimeDurationSeconds = (double) lowTimeNanos / NANOS_PER_SECOND;
    }

    private static void resetLowTimeState() {
        lowTimeStartNanos = 0;
        lowTimeDurationSeconds = 0.0;
    }

    private static void updateLowOnePercentDurationState(LazyChunksConfig config, long currentTimeNanos, int frameTimeSampleCount) {
        if (!config.adaptiveThrottleCoreBoost
                || !onePercentLowInitialized
                || frameTimeSampleCount < MIN_FRAME_TIME_SAMPLES) {
            resetAdaptiveBoostState();
            return;
        }

        boolean isBelowThreshold = onePercentLowFps < config.adaptiveLowOnePercentFpsThreshold;
        
        if (isBelowThreshold) {
            if (lowOnePercentStartNanos == 0) {
                lowOnePercentStartNanos = currentTimeNanos;
            }

            long lowDurationNanos = currentTimeNanos - lowOnePercentStartNanos;
            if (lowDurationNanos >= LOW_ONE_PERCENT_DURATION_RESET_INTERVAL_NANOS) {
                lowOnePercentStartNanos = currentTimeNanos;
            }

            adaptiveLowDurationSeconds = (double) lowDurationNanos / NANOS_PER_SECOND;
        } else {
            lowOnePercentStartNanos = 0;
            adaptiveLowDurationSeconds = 0.0;
        }
    }

    private static void updateAdaptiveBoostState(LazyChunksConfig config, long currentTimeNanos) {
        if (!config.adaptiveThrottleCoreBoost) {
            lastFps = 0;
            resetAdaptiveBoostState();
            return;
        }

        if (adaptiveLowDurationSeconds < config.adaptiveLowOnePercentDurationSeconds) {
            lastFps = 0;
            updateAdaptiveExtraCores(config, 0, currentTimeNanos);
            if (adaptiveExtraCores == 0) {
                adaptiveBoostActivated = false;
            }
            return;
        }

        if (!adaptiveBoostActivated) {
            adaptiveBoostActivated = true;
            adaptiveBoostActivationTimeNanos = currentTimeNanos;
        }

        int vanillaFps = Minecraft.getInstance().getFps();
        lastFps = vanillaFps;
        int targetExtraCores = getTargetAdaptiveExtraCores(config, vanillaFps);
        updateAdaptiveExtraCores(config, targetExtraCores, currentTimeNanos);
    }

    private static int getTargetAdaptiveExtraCores(LazyChunksConfig config, int vanillaFps) {
        if (vanillaFps <= config.adaptiveAverageFpsThreshold) {
            return 0;
        }

        int fpsAboveThreshold = vanillaFps - config.adaptiveAverageFpsThreshold;
        int extraCores = 1 + Math.max(0, fpsAboveThreshold - 1) / config.adaptiveFpsPerExtraCore;
        return Math.min(config.adaptiveMaxExtraCores, extraCores);
    }

    private static void updateAdaptiveExtraCores(LazyChunksConfig config, int targetExtraCores, long currentTimeNanos) {
        if (targetExtraCores == adaptiveExtraCores) {
            return;
        }

        long changeIntervalNanos = (long) (config.adaptiveCoreChangeIntervalSeconds * NANOS_PER_SECOND);
        if (lastAdaptiveCoreChangeNanos != 0
                && currentTimeNanos - lastAdaptiveCoreChangeNanos < changeIntervalNanos) {
            return;
        }

        adaptiveExtraCores += targetExtraCores > adaptiveExtraCores ? 1 : -1;
        lastAdaptiveCoreChangeNanos = currentTimeNanos;
    }

    private static void resetAdaptiveBoostState() {
        lowOnePercentStartNanos = 0;
        lastAdaptiveCoreChangeNanos = 0;
        adaptiveLowDurationSeconds = 0.0;
        adaptiveExtraCores = 0;
        lastFps = 0;
        adaptiveBoostActivated = false;
        adaptiveBoostActivationTimeNanos = 0;
    }

    private static double getAdaptiveBudgetBoost(LazyChunksConfig config) {
        if (!config.adaptiveThrottleCoreBoost || adaptiveExtraCores <= 0) {
            return 0.0;
        }

        return adaptiveExtraCores * config.adaptiveCoreWeight;
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
            adaptiveExtraCores = 0;
            return Integer.MAX_VALUE;
        }

        if (pendingTasks.isEmpty()) {
            lastPendingTasks = 0;
            lastWeight = 0;
            lastThrottled = false;
            adaptiveExtraCores = 0;
            return 0;
        }

        Runnable[] tasks = pendingTasks.toArray(new Runnable[0]);
        double totalWeight = getTotalChunkWeight(tasks);
        int taskCount = tasks.length;

        updateQueueMetrics(taskCount);
        updateAdaptiveBoostState(config, System.nanoTime());

        lastPendingTasks = taskCount;
        lastWeight = totalWeight;

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

        maxWeight += getAdaptiveBudgetBoost(config);

        if (config.teleportProtection) {
            maxWeight *= TeleportDetector.getBudgetMultiplier();
        }

        maxWeight = Math.max(maxWeight, config.minimumBudget);

        int limit = getCountForWeight(tasks, maxWeight);

        lastThrottled = true;
        lastBudget = maxWeight;
        lastProcessed = limit;

        return limit;
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
