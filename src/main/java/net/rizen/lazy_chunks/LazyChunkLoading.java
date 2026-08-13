package net.rizen.lazy_chunks;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import net.rizen.lazy_chunks.mixin.accessor.IPacketProcessorListenerAndPacketAccessor;

import java.util.Queue;

public class LazyChunkLoading {

    // Metrics are touched from render, tick, and packet-processing paths.
    private static final Object STATE_LOCK = new Object();
    private static final double MIN_WEIGHT_THRESHOLD = 5.0;
    private static final double WEIGHT_CHUNK_WITH_LIGHT = 1.0;
    private static final double WEIGHT_LIGHT_UPDATE = 0.2;
    private static final double WEIGHT_FORGET_CHUNK = 2.6;
    private static volatile double onePercentLowFps = 60.0;
    private static final double LOW_TIME_DISPLAY_FPS_THRESHOLD = 60.0;
    private static final int LOW_TIME_DISPLAY_WINDOW_SECONDS = 12;
    private static final int FRAME_TIME_HISTORY_SIZE = 300;
    private static final int RAW_ONE_PERCENT_LOW_HISTORY_SIZE = 9;
    private static final long LOW_TIME_DISPLAY_RESET_INTERVAL = LOW_TIME_DISPLAY_WINDOW_SECONDS * 1_000_000_000L;
    private static final double ONE_PERCENT_LOW_WORSE_SMOOTHING = 0.18;
    private static final double ONE_PERCENT_LOW_RECOVERY_SMOOTHING = 0.35;
    private static final double ONE_PERCENT_LOW_MAX_WORSE_FRAME_TIME_STEP_MS = 4.0;
    private static final double ONE_PERCENT_LOW_MAX_RECOVERY_FRAME_TIME_STEP_MS = 24.0;
    private static final double ONE_PERCENT_LOW_STRONG_RECOVERY_RATIO = 0.75;
    private static final double ONE_PERCENT_LOW_STRONG_RECOVERY_DELTA_MS = 20.0;
    private static final double[] frameTimeHistory = new double[FRAME_TIME_HISTORY_SIZE];
    private static final double[] worstFrameBuffer = new double[FRAME_TIME_HISTORY_SIZE];
    private static final double[] rawOnePercentLowFrameTimeHistory = new double[RAW_ONE_PERCENT_LOW_HISTORY_SIZE];
    private static final double[] rawOnePercentLowFrameTimeSortBuffer = new double[RAW_ONE_PERCENT_LOW_HISTORY_SIZE];
    private static int frameTimeHistoryIndex = 0;
    private static int frameTimeSampleCount = 0;
    private static int rawOnePercentLowFrameTimeIndex = 0;
    private static int rawOnePercentLowFrameTimeSampleCount = 0;
    private static double frameTimeSum = 0.0;
    private static double frameTimeSumSquares = 0.0;
    private static volatile double onePercentLowFrameTimeMs = 16.67;
    private static volatile double averageFrameTime = 16.67;
    private static volatile double frameTimeVariance = 0.0;
    private static int previousQueueDepth = 0;
    private static volatile double queueGrowthRate = 0.0;
    private static volatile double lastProcessingTimeMs = 0.0;
    private static volatile int lastPendingTasks = 0;
    private static volatile double lastWeight = 0;
    private static volatile int lastFps = 0;
    private static volatile double lastBudget = 0;
    private static volatile int lastProcessed = 0;
    private static volatile boolean lastThrottled = false;
    private static volatile int lastAdaptiveBoostCores = 0;
    private static volatile int lastAdaptiveFps = 0;
    private static volatile double lastAdaptiveLowFpsSeconds = 0.0;
    private static long lastOnePercentCalcTime = 0;
    private static long adaptiveLowFpsStartTime = 0;
    private static long lowFpsDisplayWindowStartTime = 0;
    private static final long ONE_PERCENT_CALC_INTERVAL = 500_000_000L;

    public static int getLastPendingTasks() { return lastPendingTasks; }
    public static double getLastWeight() { return lastWeight; }
    public static int getLastFps() { return lastFps; }
    public static double getLastBudget() { return lastBudget; }
    public static int getLastProcessed() { return lastProcessed; }
    public static boolean wasThrottled() { return lastThrottled; }
    public static int getLastAdaptiveBoostCores() { return lastAdaptiveBoostCores; }
    public static int getLastAdaptiveFps() { return lastAdaptiveFps; }
    public static double getLastAdaptiveLowFpsSeconds() { return Math.min(lastAdaptiveLowFpsSeconds, LOW_TIME_DISPLAY_WINDOW_SECONDS); }
    public static double getOnePercentLowFps() { return onePercentLowFps; }
    public static double getFrameTimeVariance() { return frameTimeVariance; }
    public static double getQueueGrowthRate() { return queueGrowthRate; }
    public static double getLastProcessingTimeMs() { return lastProcessingTimeMs; }

    public static void recordFrameTime(double frameTimeMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0) {
            return;
        }

        synchronized (STATE_LOCK) {
            long currentTime = System.nanoTime();
            if (frameTimeSampleCount == FRAME_TIME_HISTORY_SIZE) {
                double oldFrameTime = frameTimeHistory[frameTimeHistoryIndex];
                frameTimeSum -= oldFrameTime;
                frameTimeSumSquares -= oldFrameTime * oldFrameTime;
            } else {
                frameTimeSampleCount++;
            }

            frameTimeHistory[frameTimeHistoryIndex] = frameTimeMs;
            frameTimeHistoryIndex = (frameTimeHistoryIndex + 1) % FRAME_TIME_HISTORY_SIZE;

            frameTimeSum += frameTimeMs;
            frameTimeSumSquares += frameTimeMs * frameTimeMs;

            averageFrameTime = frameTimeSum / frameTimeSampleCount;
            frameTimeVariance = Math.max(0.0, (frameTimeSumSquares / frameTimeSampleCount) - (averageFrameTime * averageFrameTime));

            if (currentTime - lastOnePercentCalcTime >= ONE_PERCENT_CALC_INTERVAL) {
                double rawOnePercentLowFrameTimeMs = calculateRawOnePercentLowFrameTimeMs();
                double filteredOnePercentLowFrameTimeMs = filterRawOnePercentLowFrameTime(rawOnePercentLowFrameTimeMs);
                double smoothedOnePercentLowFrameTimeMs = lastOnePercentCalcTime == 0
                        ? setOnePercentLowFrameTime(filteredOnePercentLowFrameTimeMs)
                        : smoothOnePercentLowFrameTime(onePercentLowFrameTimeMs, filteredOnePercentLowFrameTimeMs);
                onePercentLowFps = 1000.0 / smoothedOnePercentLowFrameTimeMs;
                lastOnePercentCalcTime = currentTime;
            }

            updateLowTimeDisplayState(currentTime);
            updateAdaptiveLowFpsState(LazyChunksConfig.getInstance(), currentTime);
        }
    }

    private static void updateLowTimeDisplayState(long currentTime) {
        if (onePercentLowFps >= LOW_TIME_DISPLAY_FPS_THRESHOLD) {
            lowFpsDisplayWindowStartTime = 0;
            lastAdaptiveLowFpsSeconds = 0.0;
            return;
        }

        if (lowFpsDisplayWindowStartTime == 0
                || currentTime - lowFpsDisplayWindowStartTime >= LOW_TIME_DISPLAY_RESET_INTERVAL) {
            lowFpsDisplayWindowStartTime = currentTime;
            lastAdaptiveLowFpsSeconds = 0.0;
            return;
        }

        lastAdaptiveLowFpsSeconds = (currentTime - lowFpsDisplayWindowStartTime) / 1_000_000_000.0;
    }

    private static void updateAdaptiveLowFpsState(LazyChunksConfig config, long currentTime) {
        if (!config.adaptiveBudgetBoostEnabled || onePercentLowFps >= config.adaptiveLowFpsThreshold) {
            adaptiveLowFpsStartTime = 0;
            lastAdaptiveBoostCores = 0;
            lastAdaptiveFps = 0;
            return;
        }

        if (adaptiveLowFpsStartTime == 0) {
            adaptiveLowFpsStartTime = currentTime;
            return;
        }
    }

    private static double calculateRawOnePercentLowFrameTimeMs() {
        if (frameTimeSampleCount <= 0) {
            return onePercentLowFrameTimeMs;
        }

        int slowFrameCount = Math.max(1, (int) Math.ceil(frameTimeSampleCount * 0.01));
        double slowFrameTimeSum = 0.0;
        int countedFrames = 0;
        int fastestWorstIndex = 0;
        double fastestWorstFrameTime = Double.POSITIVE_INFINITY;

        for (int i = 0; i < frameTimeSampleCount; i++) {
            double frameTime = frameTimeHistory[i];

            if (countedFrames < slowFrameCount) {
                worstFrameBuffer[countedFrames] = frameTime;
                countedFrames++;
                if (frameTime < fastestWorstFrameTime) {
                    fastestWorstFrameTime = frameTime;
                    fastestWorstIndex = countedFrames - 1;
                }
            } else if (frameTime > fastestWorstFrameTime) {
                worstFrameBuffer[fastestWorstIndex] = frameTime;
                fastestWorstIndex = 0;
                fastestWorstFrameTime = worstFrameBuffer[0];
                for (int selected = 1; selected < slowFrameCount; selected++) {
                    double selectedFrameTime = worstFrameBuffer[selected];
                    if (selectedFrameTime < fastestWorstFrameTime) {
                        fastestWorstFrameTime = selectedFrameTime;
                        fastestWorstIndex = selected;
                    }
                }
            }
        }

        for (int i = 0; i < countedFrames; i++) {
            slowFrameTimeSum += worstFrameBuffer[i];
        }

        if (slowFrameTimeSum <= 0.0) {
            return onePercentLowFrameTimeMs;
        }

        return slowFrameTimeSum / countedFrames;
    }

    private static double filterRawOnePercentLowFrameTime(double rawFrameTimeMs) {
        if (!Double.isFinite(rawFrameTimeMs) || rawFrameTimeMs <= 0.0) {
            return onePercentLowFrameTimeMs;
        }

        if (rawFrameTimeMs < onePercentLowFrameTimeMs * ONE_PERCENT_LOW_STRONG_RECOVERY_RATIO
                || onePercentLowFrameTimeMs - rawFrameTimeMs >= ONE_PERCENT_LOW_STRONG_RECOVERY_DELTA_MS) {
            resetRawOnePercentLowFrameTimeHistory(rawFrameTimeMs);
        }

        rawOnePercentLowFrameTimeHistory[rawOnePercentLowFrameTimeIndex] = rawFrameTimeMs;
        rawOnePercentLowFrameTimeIndex = (rawOnePercentLowFrameTimeIndex + 1) % RAW_ONE_PERCENT_LOW_HISTORY_SIZE;
        if (rawOnePercentLowFrameTimeSampleCount < RAW_ONE_PERCENT_LOW_HISTORY_SIZE) {
            rawOnePercentLowFrameTimeSampleCount++;
        }

        System.arraycopy(rawOnePercentLowFrameTimeHistory, 0, rawOnePercentLowFrameTimeSortBuffer, 0, rawOnePercentLowFrameTimeSampleCount);
        insertionSort(rawOnePercentLowFrameTimeSortBuffer, rawOnePercentLowFrameTimeSampleCount);

        int middle = rawOnePercentLowFrameTimeSampleCount / 2;
        if ((rawOnePercentLowFrameTimeSampleCount & 1) == 1) {
            return rawOnePercentLowFrameTimeSortBuffer[middle];
        }

        return (rawOnePercentLowFrameTimeSortBuffer[middle - 1] + rawOnePercentLowFrameTimeSortBuffer[middle]) * 0.5;
    }

    private static void resetRawOnePercentLowFrameTimeHistory(double frameTimeMs) {
        rawOnePercentLowFrameTimeIndex = 0;
        rawOnePercentLowFrameTimeSampleCount = 0;
        for (int i = 0; i < RAW_ONE_PERCENT_LOW_HISTORY_SIZE; i++) {
            rawOnePercentLowFrameTimeHistory[i] = frameTimeMs;
            rawOnePercentLowFrameTimeSortBuffer[i] = frameTimeMs;
        }
    }

    private static double smoothOnePercentLowFrameTime(double currentFrameTimeMs, double targetFrameTimeMs) {
        if (!Double.isFinite(targetFrameTimeMs) || targetFrameTimeMs <= 0.0) {
            return currentFrameTimeMs;
        }

        if (!Double.isFinite(currentFrameTimeMs) || currentFrameTimeMs <= 0.0) {
            onePercentLowFrameTimeMs = targetFrameTimeMs;
            return targetFrameTimeMs;
        }

        double smoothing = targetFrameTimeMs > currentFrameTimeMs
                ? ONE_PERCENT_LOW_WORSE_SMOOTHING
                : ONE_PERCENT_LOW_RECOVERY_SMOOTHING;
        double smoothedFrameTimeMs = currentFrameTimeMs + (targetFrameTimeMs - currentFrameTimeMs) * smoothing;

        if (targetFrameTimeMs > currentFrameTimeMs) {
            smoothedFrameTimeMs = Math.min(smoothedFrameTimeMs, currentFrameTimeMs + ONE_PERCENT_LOW_MAX_WORSE_FRAME_TIME_STEP_MS);
        } else {
            smoothedFrameTimeMs = Math.max(smoothedFrameTimeMs, currentFrameTimeMs - ONE_PERCENT_LOW_MAX_RECOVERY_FRAME_TIME_STEP_MS);
        }

        onePercentLowFrameTimeMs = Math.max(1.0, smoothedFrameTimeMs);
        return onePercentLowFrameTimeMs;
    }

    private static double setOnePercentLowFrameTime(double frameTimeMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0.0) {
            return onePercentLowFrameTimeMs;
        }

        onePercentLowFrameTimeMs = Math.max(1.0, frameTimeMs);
        return onePercentLowFrameTimeMs;
    }

    private static void insertionSort(double[] values, int length) {
        for (int i = 1; i < length; i++) {
            double value = values[i];
            int previous = i - 1;
            while (previous >= 0 && values[previous] > value) {
                values[previous + 1] = values[previous];
                previous--;
            }
            values[previous + 1] = value;
        }
    }

    public static void recordProcessingTime(double timeMs) {
        synchronized (STATE_LOCK) {
            lastProcessingTimeMs = timeMs;
        }
    }

    private static void updateQueueMetrics(int currentDepth) {
        queueGrowthRate = queueGrowthRate * 0.8 + (currentDepth - previousQueueDepth) * 0.2;
        previousQueueDepth = currentDepth;
    }

    private static double getStabilityMultiplier() {
        if (averageFrameTime <= 0) return 1.0;
        double stabilityFactor = 1.0 / (1.0 + Math.sqrt(Math.max(0, frameTimeVariance)) / averageFrameTime);
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

    private static int getAdaptiveBoostCores(LazyChunksConfig config, long currentTime) {
        if (!config.adaptiveBudgetBoostEnabled || adaptiveLowFpsStartTime == 0) {
            lastAdaptiveFps = 0;
            lastAdaptiveBoostCores = 0;
            return 0;
        }

        double lowFpsSeconds = (currentTime - adaptiveLowFpsStartTime) / 1_000_000_000.0;
        if (lowFpsSeconds < config.adaptiveLowFpsDurationSeconds) {
            lastAdaptiveFps = 0;
            lastAdaptiveBoostCores = 0;
            return 0;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int vanillaFps = minecraft == null ? 1 : Math.max(minecraft.getFps(), 1);
        lastFps = vanillaFps;
        lastAdaptiveFps = vanillaFps;
        if (vanillaFps <= config.adaptiveAverageFpsThreshold) {
            lastAdaptiveBoostCores = Math.max(0, lastAdaptiveBoostCores - 1);
            return lastAdaptiveBoostCores;
        }

        int fpsHeadroom = vanillaFps - config.adaptiveAverageFpsThreshold;
        int cores = 1 + (fpsHeadroom / Math.max(config.adaptiveFpsPerBoostCore, 1));
        cores = Math.max(1, Math.min(config.adaptiveMaxBoostCores, cores));
        lastAdaptiveBoostCores = cores;
        return cores;
    }

    public static long getMaxProcessingTimeNanos() {
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        double targetFrameTimeMs = 1000.0 / Math.max(config.targetFps, 1);
        double maxTimeMs = targetFrameTimeMs * (config.maxFrameTimePercent / 100.0);
        return (long) (maxTimeMs * 1_000_000);
    }

    public static int getTaskCount(Queue<?> pendingTasks) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        synchronized (STATE_LOCK) {
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

            Object[] tasks = pendingTasks.toArray();
            double totalWeight = 0.0;
            boolean hasUnsupportedTasks = false;
            for (Object task : tasks) {
                if (task instanceof IPacketProcessorListenerAndPacketAccessor packetTaskAccessor) {
                    totalWeight += getPacketWeight(packetTaskAccessor.lazychunks$getPacket());
                } else {
                    hasUnsupportedTasks = true;
                }
            }
            int taskCount = tasks.length;

            updateQueueMetrics(taskCount);

            lastPendingTasks = taskCount;
            lastWeight = totalWeight;

            if (hasUnsupportedTasks) {
                lastThrottled = false;
                lastBudget = totalWeight;
                lastProcessed = taskCount;
                return Integer.MAX_VALUE;
            }

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

            int adaptiveBoostCores = getAdaptiveBoostCores(config, System.nanoTime());
            maxWeight += adaptiveBoostCores * config.adaptiveBoostWeightPerCore;

            maxWeight = Math.max(maxWeight, config.minimumBudget);

            int limit = getCountForWeight(tasks, maxWeight);

            lastThrottled = true;
            lastBudget = maxWeight;
            lastProcessed = limit;

            return limit;
        }
    }

    private static int getCountForWeight(Object[] tasks, double maxWeight) {
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

    private static double getTotalChunkWeight(Object[] tasks) {
        double weight = 0.0;

        for (Object task : tasks) {
            weight += getChunkUpdateWeight(task);
        }

        return weight;
    }

    private static double getChunkUpdateWeight(Object task) {
        if (task instanceof IPacketProcessorListenerAndPacketAccessor packetTaskAccessor) {
            return getPacketWeight(packetTaskAccessor.lazychunks$getPacket());
        }

        return 0.0;
    }

    private static double getPacketWeight(Packet<?> packet) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket) {
            return WEIGHT_CHUNK_WITH_LIGHT;
        }

        if (packet instanceof ClientboundLightUpdatePacket) {
            return WEIGHT_LIGHT_UPDATE;
        }

        if (packet instanceof ClientboundForgetLevelChunkPacket) {
            return WEIGHT_FORGET_CHUNK;
        }

        return 0.0;
    }
}
