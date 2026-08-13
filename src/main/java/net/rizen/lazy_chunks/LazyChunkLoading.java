package net.rizen.lazy_chunks;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.Arrays;
import java.util.Queue;

@SuppressWarnings({"unused", "FieldMayBeFinal", "LocalVariableMayBeFinal"})
public class LazyChunkLoading {
    private static final double FULL_CHUNK_WEIGHT = 1.0D;
    private static final double LIGHT_UPDATE_WEIGHT = 0.2D;
    private static final double UNLOAD_CHUNK_WEIGHT = 2.6D;
    private static final double BIOME_CHUNK_WEIGHT = 0.6D;
    private static final double SECTION_BLOCK_UPDATE_WEIGHT = 0.4D;
    private static final double SINGLE_BLOCK_UPDATE_WEIGHT = 0.15D;
    private static final double MIN_TELEPORT_BUDGET = 0.75D;
    private static final long DEFAULT_MAX_PROCESSING_TIME_NANOS = 1_000_000L;
    private static final double ONE_PERCENT_LOW_SAMPLE_RATIO = 0.015D;
    private static final double ISOLATED_SLOW_TAIL_RATIO = 0.01D;
    private static final double ISOLATED_SLOW_TAIL_GAP_RATIO = 1.75D;
    private static final int ISOLATED_SLOW_TAIL_IQR_MULTIPLIER = 6;
    private static final int FPS_HEADROOM_PER_BOOST_LEVEL = 60;

    private static int lastPendingTasks = 0;
    private static double lastWeight = 0.0;
    private static double lastBudget = 0.0;
    private static double lastProcessingTimeMs = 0.0;
    private static double queueGrowthRate = 0.0;
    private static boolean wasThrottled = false;
    private static long lastMaxProcessingTimeNanos = DEFAULT_MAX_PROCESSING_TIME_NANOS;
    private static int previousPendingTasks = 0;
    private static volatile long lastFrameIntervalNs = 0L;
    private static volatile long lowFpsBelowThresholdSinceMs = 0L;
    private static volatile int adaptiveCoreBoostLevel = 0;
    private static volatile int lastVanillaAverageFps = 0;
    private static volatile boolean adaptiveAverageTrackingActive = false;

    // Real frame interval samples use up to 300 recent frames. Low Time resets every 12 seconds
    // without clearing the rolling 1% low FPS samples.
    private static final Object FRAME_INTERVAL_LOCK = new Object();
    private static final int MAX_FRAME_SAMPLES = 300;
    private static final int MIN_FRAME_SAMPLES = 30;
    private static final long LOW_TIME_RESET_INTERVAL_MS = 12_000L;
    private static final long[] frameIntervalNsSamples = new long[MAX_FRAME_SAMPLES];
    private static final long[] sortedFrameIntervalNsSamples = new long[MAX_FRAME_SAMPLES];
    private static int frameIntervalWriteIndex = 0;
    private static int frameIntervalSampleCount = 0;
    private static int cachedOnePercentLowFps = 0;
    private static long lastFpsUpdateTime = 0L;
    private static final long FPS_UPDATE_INTERVAL = 2000L;
    private static boolean hasLoggedFirstFrame = false;

    public static void recordFrameIntervalNs(long frameIntervalNs) {
        if (!hasLoggedFirstFrame) {
            LazyChunksMod.LOGGER.info("Real frame interval recording is active for 1% low FPS");
            hasLoggedFirstFrame = true;
        }

        if (frameIntervalNs > 100_000L && frameIntervalNs < 1_000_000_000L) {
            synchronized (FRAME_INTERVAL_LOCK) {
                lastFrameIntervalNs = frameIntervalNs;
                frameIntervalNsSamples[frameIntervalWriteIndex] = frameIntervalNs;
                frameIntervalWriteIndex = (frameIntervalWriteIndex + 1) % MAX_FRAME_SAMPLES;
                if (frameIntervalSampleCount < MAX_FRAME_SAMPLES) {
                    frameIntervalSampleCount++;
                }
            }
        }
    }

    public static void recordVanillaAverageFps(int averageFps) {
        if (!adaptiveAverageTrackingActive) {
            return;
        }
        lastVanillaAverageFps = Math.max(0, averageFps);
    }

    public static int getOnePercentLowFps() {
        long now = System.currentTimeMillis();

        synchronized (FRAME_INTERVAL_LOCK) {
            if (now - lastFpsUpdateTime < FPS_UPDATE_INTERVAL) {
                updateLowTimeTracking(cachedOnePercentLowFps, now);
                return cachedOnePercentLowFps;
            }
            lastFpsUpdateTime = now;

            if (frameIntervalSampleCount < MIN_FRAME_SAMPLES) {
                cachedOnePercentLowFps = 0;
                updateLowTimeTracking(cachedOnePercentLowFps, now);
                return cachedOnePercentLowFps;
            }

            cachedOnePercentLowFps = calculateOnePercentLowFps(frameIntervalSampleCount);
            updateLowTimeTracking(cachedOnePercentLowFps, now);
            return cachedOnePercentLowFps;
        }
    }

    public static int getLowTimeWindowElapsedSeconds() {
        long lowTimeStartedAtMs = lowFpsBelowThresholdSinceMs;

        if (lowTimeStartedAtMs == 0L) {
            return 0;
        }

        long elapsedMs = Math.max(0L, System.currentTimeMillis() - lowTimeStartedAtMs);
        return (int) Math.min(getLowTimeWindowDurationSeconds(), elapsedMs / 1000L);
    }

    public static int getLowTimeWindowDurationSeconds() {
        return (int) (LOW_TIME_RESET_INTERVAL_MS / 1000L);
    }

    private static void updateLowTimeTracking(int lowFps, long nowMs) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        if (lowFps <= 0 || lowFps >= config.adaptiveLowFpsThreshold) {
            resetAdaptiveCoreBoost();
            return;
        }

        if (lowFpsBelowThresholdSinceMs == 0L
                || nowMs - lowFpsBelowThresholdSinceMs >= LOW_TIME_RESET_INTERVAL_MS) {
            resetAdaptiveCoreBoost();
            lowFpsBelowThresholdSinceMs = nowMs;
        }
    }

    private static int calculateOnePercentLowFps(int sampleCount) {
        System.arraycopy(frameIntervalNsSamples, 0, sortedFrameIntervalNsSamples, 0, sampleCount);
        Arrays.sort(sortedFrameIntervalNsSamples, 0, sampleCount);

        int effectiveSampleCount = trimIsolatedSlowTail(sampleCount);
        int worstSampleCount = Math.max(1, (int) Math.ceil(effectiveSampleCount * ONE_PERCENT_LOW_SAMPLE_RATIO));
        long onePercentBoundaryFrameTimeNs = sortedFrameIntervalNsSamples[effectiveSampleCount - worstSampleCount];
        return (int) (1_000_000_000L / onePercentBoundaryFrameTimeNs);
    }

    private static int trimIsolatedSlowTail(int sampleCount) {
        int maxTailSamples = Math.max(1, (int) Math.ceil(sampleCount * ISOLATED_SLOW_TAIL_RATIO));
        if (maxTailSamples <= 0) {
            return sampleCount;
        }

        long q1 = sortedFrameIntervalNsSamples[sampleCount / 4];
        long median = sortedFrameIntervalNsSamples[sampleCount / 2];
        long q3 = sortedFrameIntervalNsSamples[(sampleCount * 3) / 4];
        long iqr = Math.max(1L, q3 - q1);
        long gapTolerance = Math.max(iqr * ISOLATED_SLOW_TAIL_IQR_MULTIPLIER, median / 2L);
        int firstTailIndex = sampleCount - maxTailSamples;
        int effectiveSampleCount = sampleCount;

        for (int i = sampleCount - 1; i >= firstTailIndex; i--) {
            long slowerFrameTimeNs = sortedFrameIntervalNsSamples[i];
            long fasterFrameTimeNs = sortedFrameIntervalNsSamples[i - 1];
            long gap = slowerFrameTimeNs - fasterFrameTimeNs;

            if (gap > gapTolerance
                    && slowerFrameTimeNs >= (long) (fasterFrameTimeNs * ISOLATED_SLOW_TAIL_GAP_RATIO)) {
                effectiveSampleCount = i;
            }
        }

        return effectiveSampleCount;
    }

    public static boolean beginFrame(Queue<?> queue) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        int pendingTasks = queue.size();
        double pendingWeight = getPendingWeight(queue);

        queueGrowthRate = (queueGrowthRate * 0.75D) + ((pendingTasks - previousPendingTasks) * 0.25D);
        previousPendingTasks = pendingTasks;

        lastPendingTasks = pendingTasks;
        lastWeight = pendingWeight;
        lastMaxProcessingTimeNanos = calculateMaxProcessingTimeNanos(config);

        if (!config.lazyChunkLoadingEnabled || pendingWeight <= 0.0D) {
            lastBudget = Math.max(0.1D, config.minimumBudget);
            wasThrottled = false;
            return false;
        }

        int lowFps = getOnePercentLowFps();
        double effectiveFps = getEffectiveFps(config, lowFps);
        double budget = calculateWeightBudget(config, effectiveFps, lowFps);
        lastBudget = budget;

        boolean aboveFpsThreshold = effectiveFps >= config.fpsThreshold && !TeleportDetector.isTeleportRecovery();
        boolean unstableFrameTime = config.proactiveThrottling && isFrameTimeUnstable(config);
        boolean backlogExceedsBudget = pendingWeight > budget;
        boolean teleportRecovery = config.teleportProtection && TeleportDetector.isTeleportRecovery();

        wasThrottled = !aboveFpsThreshold
                && (backlogExceedsBudget || unstableFrameTime || teleportRecovery);

        return wasThrottled;
    }

    public static void finishFrame(double processedWeight, double processingTimeMs) {
        lastProcessingTimeMs = processingTimeMs;
    }

    public static void recordProcessingTime(double processingTimeMs) {
        lastProcessingTimeMs = processingTimeMs;
    }

    public static int getTaskCount(Queue<?> queue) {
        return queue.size();
    }

    public static long getMaxProcessingTimeNanos() {
        return lastMaxProcessingTimeNanos;
    }

    public static boolean canProcessPacket(Packet<?> packet, double processedWeight, boolean processedWeightedPacket) {
        double packetWeight = getPacketWeight(packet);
        if (packetWeight <= 0.0D) {
            return true;
        }

        if (!processedWeightedPacket) {
            return true;
        }

        return processedWeight + packetWeight <= lastBudget;
    }

    public static double getPacketWeight(Packet<?> packet) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket) {
            return FULL_CHUNK_WEIGHT;
        }

        if (packet instanceof ClientboundLightUpdatePacket) {
            return LIGHT_UPDATE_WEIGHT;
        }

        if (packet instanceof ClientboundForgetLevelChunkPacket) {
            return UNLOAD_CHUNK_WEIGHT;
        }

        if (packet instanceof ClientboundChunksBiomesPacket) {
            return BIOME_CHUNK_WEIGHT;
        }

        if (packet instanceof ClientboundSectionBlocksUpdatePacket) {
            return SECTION_BLOCK_UPDATE_WEIGHT;
        }

        if (packet instanceof ClientboundBlockUpdatePacket || packet instanceof ClientboundBlockEntityDataPacket) {
            return SINGLE_BLOCK_UPDATE_WEIGHT;
        }

        return 0.0D;
    }

    private static double getPendingWeight(Queue<?> queue) {
        double weight = 0.0D;
        for (Object task : queue) {
            if (task instanceof net.rizen.lazy_chunks.mixin.accessor.IPacketProcessorListenerAndPacketAccessor packetTask) {
                weight += getPacketWeight(packetTask.lazychunks$getPacket());
            }
        }
        return weight;
    }

    private static double calculateWeightBudget(LazyChunksConfig config, double effectiveFps, int lowFps) {
        double budget = config.baseWeightPerFrame * (effectiveFps / Math.max(1.0D, config.targetFps));
        budget = Math.max(config.minimumBudget, budget);

        if (config.proactiveThrottling && isFrameTimeUnstable(config)) {
            budget *= 0.65D;
        }

        int boostLevel = updateAdaptiveCoreBoost(config, lowFps);
        if (boostLevel > 0) {
            budget += boostLevel * config.adaptiveCoreBoostWeight;
        }

        if (config.teleportProtection && TeleportDetector.isTeleportRecovery()) {
            budget *= TeleportDetector.getBudgetMultiplier();
            budget = Math.max(MIN_TELEPORT_BUDGET, budget);
        }

        return Math.max(0.1D, budget);
    }

    private static int updateAdaptiveCoreBoost(LazyChunksConfig config, int lowFps) {
        if (!config.adaptiveCoreBoostEnabled || lowFps <= 0) {
            resetAdaptiveCoreBoost();
            return adaptiveCoreBoostLevel;
        }

        long now = System.currentTimeMillis();
        if (lowFps >= config.adaptiveLowFpsThreshold) {
            resetAdaptiveCoreBoost();
            return adaptiveCoreBoostLevel;
        }

        if (lowFpsBelowThresholdSinceMs == 0L) {
            lowFpsBelowThresholdSinceMs = now;
        }

        long lowFpsDurationMs = now - lowFpsBelowThresholdSinceMs;
        if (lowFpsDurationMs < config.adaptiveLowFpsDurationSeconds * 1000L) {
            adaptiveCoreBoostLevel = 0;
            adaptiveAverageTrackingActive = false;
            lastVanillaAverageFps = 0;
            return adaptiveCoreBoostLevel;
        }

        // 达到持续时间要求后，开始追踪平均 FPS
        if (!adaptiveAverageTrackingActive) {
            adaptiveAverageTrackingActive = true;
            // 不要重置 lastVanillaAverageFps，保留已有的数据
            return adaptiveCoreBoostLevel;
        }

        // 只有当有有效的平均 FPS 数据时才计算核心等级
        adaptiveAverageTrackingActive = true;
        int averageFps = lastVanillaAverageFps;
        
        // 如果还没有收集到平均 FPS 数据，等待下一帧
        if (averageFps <= 0) {
            return 0;
        }
        
        adaptiveCoreBoostLevel = calculateAdaptiveCoreBoostLevel(config, averageFps);
        return adaptiveCoreBoostLevel;
    }

    private static int calculateAdaptiveCoreBoostLevel(LazyChunksConfig config, int averageFps) {
        if (averageFps <= config.adaptiveAverageFpsThreshold) {
            return 0;
        }

        int boostLevel = 1 + ((averageFps - config.adaptiveAverageFpsThreshold) / FPS_HEADROOM_PER_BOOST_LEVEL);
        return Math.max(1, Math.min(config.adaptiveCoreBoostMax, boostLevel));
    }

    private static void resetAdaptiveCoreBoost() {
        lowFpsBelowThresholdSinceMs = 0L;
        adaptiveCoreBoostLevel = 0;
        adaptiveAverageTrackingActive = false;
        lastVanillaAverageFps = 0;
    }

    private static long calculateMaxProcessingTimeNanos(LazyChunksConfig config) {
        double targetFrameNanos = 1_000_000_000.0D / Math.max(1, config.targetFps);
        double budgetNanos = targetFrameNanos * (config.maxFrameTimePercent / 100.0D);
        return Math.max(250_000L, (long) budgetNanos);
    }

    private static boolean isFrameTimeUnstable(LazyChunksConfig config) {
        if (lastFrameIntervalNs <= 0L) {
            return false;
        }

        double targetFrameNanos = 1_000_000_000.0D / Math.max(1, config.targetFps);
        return lastFrameIntervalNs > targetFrameNanos * 1.15D;
    }

    private static double getEffectiveFps(LazyChunksConfig config, int lowFps) {
        if (lowFps > 0) {
            return lowFps;
        }

        if (lastFrameIntervalNs > 0L) {
            return 1_000_000_000.0D / lastFrameIntervalNs;
        }

        return config.targetFps;
    }

    public static int getLastPendingTasks() { return lastPendingTasks; }
    public static double getLastWeight() { return lastWeight; }
    public static double getLastBudget() { return lastBudget; }
    public static double getLastProcessingTimeMs() { return lastProcessingTimeMs; }
    public static double getQueueGrowthRate() { return queueGrowthRate; }
    public static boolean wasThrottled() { return wasThrottled; }
    public static int getAdaptiveCoreBoostLevel() { return adaptiveCoreBoostLevel; }
    public static int getLastVanillaAverageFps() { return lastVanillaAverageFps; }
    public static boolean shouldTrackVanillaAverageFps() { return adaptiveAverageTrackingActive; }
}
