package net.rizen.lazy_chunks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.rizen.lazy_chunks.LazyChunksMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LazyChunksConfig {
    private static LazyChunksConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("lazy_chunks.json");
    private static final int MIN_FPS = 1;
    private static final int MAX_FPS = 1000;
    private static final double MIN_BASE_WEIGHT_PER_FRAME = 0.1;
    private static final double MAX_BASE_WEIGHT_PER_FRAME = 1000.0;
    private static final double MIN_FRAME_TIME_PERCENT = 1.0;
    private static final double MAX_FRAME_TIME_PERCENT = 20.0;
    private static final double MINIMUM_SAFE_BUDGET = 2.6;
    private static final double MAX_MINIMUM_BUDGET = 1000.0;
    private static final double MIN_ADAPTIVE_LOW_SECONDS = 1.0;
    private static final double MAX_ADAPTIVE_LOW_SECONDS = 12.0;
    private static final int MIN_ADAPTIVE_EXTRA_CORES = 1;
    private static final int MAX_ADAPTIVE_EXTRA_CORES = 3;
    private static final int MIN_ADAPTIVE_FPS_STEP = 1;
    private static final int MAX_ADAPTIVE_FPS_STEP = 1000;
    private static final double MIN_ADAPTIVE_CORE_WEIGHT = 0.1;
    private static final double MAX_ADAPTIVE_CORE_WEIGHT = 1000.0;

    /**
     * Enable or disable the Lazy Chunk Loading feature.
     * When enabled, terrain loading is spread across multiple frames to reduce stutters.
     */
    public boolean lazyChunkLoadingEnabled = true;

    /**
     * Show LazyChunks status lines on the F3 debug screen.
     */
    public boolean showDebugInfo = true;

    /**
     * The baseline FPS for budget calculation.
     * At this FPS, the game processes exactly 'baseWeightPerFrame' worth of chunks per frame.
     * Lower FPS = slower chunk loading, Higher FPS = faster chunk loading.
     */
    public int targetFps = 60;

    /**
     * FPS threshold above which throttling is disabled.
     * When your FPS is above this value, chunks load instantly (vanilla behavior).
     * When below, gradual loading kicks in to prevent stutters.
     */
    public int fpsThreshold = 80;

    /**
     * How much chunk "weight" to process per frame at targetFps.
     * Higher values = faster terrain loading but more stutters.
     * Lower values = slower terrain loading but smoother gameplay.
     * (Chunk weights: full chunk = 1.0, light update = 0.2, unload = 2.6)
     */
    public double baseWeightPerFrame = 3.0;

    /**
     * Maximum percentage of frame time to spend on chunk processing (1-20).
     * Lower values = smoother gameplay but slower chunk loading.
     * Default 12% means at 60 FPS (~16.7ms frame), max ~2ms for chunk processing.
     */
    public double maxFrameTimePercent = 12.0;

    /**
     * Enable proactive throttling based on frame time stability.
     * When enabled, reduces chunk loading when frame times are unstable
     * to prevent stutters before they happen.
     */
    public boolean proactiveThrottling = true;

    /**
     * Enable teleport protection.
     * When enabled, applies extra conservative throttling after teleporting
     * or changing dimensions to handle the chunk loading spike smoothly.
     */
    public boolean teleportProtection = true;

    /**
     * Minimum weight budget - must be >= heaviest packet weight (2.6).
     * Ensures at least one chunk packet is always processed to prevent stalls.
     */
    public double minimumBudget = 3.0;

    /**
     * Enable adaptive budget boost when 1% low FPS stays poor while vanilla FPS still has headroom.
     */
    public boolean adaptiveThrottleCoreBoost = true;

    /**
     * 1% low FPS threshold that starts the adaptive low-FPS timer.
     */
    public int adaptiveLowOnePercentFpsThreshold = 60;

    /**
     * How long 1% low FPS must stay below the adaptive threshold before vanilla FPS checks can start.
     * This is capped to the 12-second 1% low sampling window.
     */
    public double adaptiveLowOnePercentDurationSeconds = 10.0;

    /**
     * Vanilla FPS must be above this value before the adaptive boost adds extra processing cores.
     * This is only checked after the 1% low FPS duration requirement has been met.
     */
    public int adaptiveAverageFpsThreshold = 120;

    /**
     * Maximum extra adaptive cores to add to the chunk budget (1-3).
     */
    public int adaptiveMaxExtraCores = 3;

    /**
     * Additional vanilla FPS required for each extra adaptive core after the first one.
     * Example: threshold 120, step 60 => 121 FPS adds 1 core, 181 adds 2, 241 adds 3.
     */
    public int adaptiveFpsPerExtraCore = 60;

    /**
     * Chunk budget weight added by each adaptive core.
     */
    public double adaptiveCoreWeight = 1.0;

    public static LazyChunksConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static LazyChunksConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                LazyChunksConfig config = GSON.fromJson(json, LazyChunksConfig.class);
                if (config != null) {
                    LazyChunksMod.LOGGER.info("Loaded config from {}", CONFIG_PATH);
                    if (config.validate() || hasMissingConfigKeys(json)) {
                        config.save();
                    }
                    return config;
                }
            } catch (IOException | JsonParseException e) {
                LazyChunksMod.LOGGER.error("Failed to load config", e);
            }
        }

        LazyChunksConfig config = new LazyChunksConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
            LazyChunksMod.LOGGER.info("Saved config to {}", CONFIG_PATH);
        } catch (IOException e) {
            LazyChunksMod.LOGGER.error("Failed to save config", e);
        }
    }

    public static void reload() {
        INSTANCE = load();
        LazyChunksMod.LOGGER.info("Config reloaded");
    }

    private boolean validate() {
        boolean changed = false;

        int validatedTargetFps = clamp(targetFps, MIN_FPS, MAX_FPS);
        if (targetFps != validatedTargetFps) {
            logCorrection("targetFps", targetFps, validatedTargetFps);
            targetFps = validatedTargetFps;
            changed = true;
        }

        int validatedFpsThreshold = clamp(fpsThreshold, MIN_FPS, MAX_FPS);
        if (fpsThreshold != validatedFpsThreshold) {
            logCorrection("fpsThreshold", fpsThreshold, validatedFpsThreshold);
            fpsThreshold = validatedFpsThreshold;
            changed = true;
        }

        double validatedBaseWeight = sanitizeDouble(
                baseWeightPerFrame,
                3.0,
                MIN_BASE_WEIGHT_PER_FRAME,
                MAX_BASE_WEIGHT_PER_FRAME
        );
        if (Double.compare(baseWeightPerFrame, validatedBaseWeight) != 0) {
            logCorrection("baseWeightPerFrame", baseWeightPerFrame, validatedBaseWeight);
            baseWeightPerFrame = validatedBaseWeight;
            changed = true;
        }

        double validatedMaxFrameTimePercent = sanitizeDouble(
                maxFrameTimePercent,
                12.0,
                MIN_FRAME_TIME_PERCENT,
                MAX_FRAME_TIME_PERCENT
        );
        if (Double.compare(maxFrameTimePercent, validatedMaxFrameTimePercent) != 0) {
            logCorrection("maxFrameTimePercent", maxFrameTimePercent, validatedMaxFrameTimePercent);
            maxFrameTimePercent = validatedMaxFrameTimePercent;
            changed = true;
        }

        double validatedMinimumBudget = sanitizeDouble(
                minimumBudget,
                3.0,
                MINIMUM_SAFE_BUDGET,
                MAX_MINIMUM_BUDGET
        );
        if (Double.compare(minimumBudget, validatedMinimumBudget) != 0) {
            logCorrection("minimumBudget", minimumBudget, validatedMinimumBudget);
            minimumBudget = validatedMinimumBudget;
            changed = true;
        }

        int validatedAdaptiveLowThreshold = clamp(adaptiveLowOnePercentFpsThreshold, MIN_FPS, MAX_FPS);
        if (adaptiveLowOnePercentFpsThreshold != validatedAdaptiveLowThreshold) {
            logCorrection("adaptiveLowOnePercentFpsThreshold", adaptiveLowOnePercentFpsThreshold, validatedAdaptiveLowThreshold);
            adaptiveLowOnePercentFpsThreshold = validatedAdaptiveLowThreshold;
            changed = true;
        }

        double validatedAdaptiveLowSeconds = sanitizeDouble(
                adaptiveLowOnePercentDurationSeconds,
                10.0,
                MIN_ADAPTIVE_LOW_SECONDS,
                MAX_ADAPTIVE_LOW_SECONDS
        );
        if (Double.compare(adaptiveLowOnePercentDurationSeconds, validatedAdaptiveLowSeconds) != 0) {
            logCorrection("adaptiveLowOnePercentDurationSeconds", adaptiveLowOnePercentDurationSeconds, validatedAdaptiveLowSeconds);
            adaptiveLowOnePercentDurationSeconds = validatedAdaptiveLowSeconds;
            changed = true;
        }

        int validatedAdaptiveAverageThreshold = clamp(adaptiveAverageFpsThreshold, MIN_FPS, MAX_FPS);
        if (adaptiveAverageFpsThreshold != validatedAdaptiveAverageThreshold) {
            logCorrection("adaptiveAverageFpsThreshold", adaptiveAverageFpsThreshold, validatedAdaptiveAverageThreshold);
            adaptiveAverageFpsThreshold = validatedAdaptiveAverageThreshold;
            changed = true;
        }

        int validatedAdaptiveMaxCores = clamp(adaptiveMaxExtraCores, MIN_ADAPTIVE_EXTRA_CORES, MAX_ADAPTIVE_EXTRA_CORES);
        if (adaptiveMaxExtraCores != validatedAdaptiveMaxCores) {
            logCorrection("adaptiveMaxExtraCores", adaptiveMaxExtraCores, validatedAdaptiveMaxCores);
            adaptiveMaxExtraCores = validatedAdaptiveMaxCores;
            changed = true;
        }

        int validatedAdaptiveFpsStep = clamp(adaptiveFpsPerExtraCore, MIN_ADAPTIVE_FPS_STEP, MAX_ADAPTIVE_FPS_STEP);
        if (adaptiveFpsPerExtraCore != validatedAdaptiveFpsStep) {
            logCorrection("adaptiveFpsPerExtraCore", adaptiveFpsPerExtraCore, validatedAdaptiveFpsStep);
            adaptiveFpsPerExtraCore = validatedAdaptiveFpsStep;
            changed = true;
        }

        double validatedAdaptiveCoreWeight = sanitizeDouble(
                adaptiveCoreWeight,
                1.0,
                MIN_ADAPTIVE_CORE_WEIGHT,
                MAX_ADAPTIVE_CORE_WEIGHT
        );
        if (Double.compare(adaptiveCoreWeight, validatedAdaptiveCoreWeight) != 0) {
            logCorrection("adaptiveCoreWeight", adaptiveCoreWeight, validatedAdaptiveCoreWeight);
            adaptiveCoreWeight = validatedAdaptiveCoreWeight;
            changed = true;
        }

        return changed;
    }

    private static boolean hasMissingConfigKeys(String json) {
        return !json.contains("\"showDebugInfo\"")
                || !json.contains("\"adaptiveThrottleCoreBoost\"")
                || !json.contains("\"adaptiveLowOnePercentFpsThreshold\"")
                || !json.contains("\"adaptiveLowOnePercentDurationSeconds\"")
                || !json.contains("\"adaptiveAverageFpsThreshold\"")
                || !json.contains("\"adaptiveMaxExtraCores\"")
                || !json.contains("\"adaptiveFpsPerExtraCore\"")
                || !json.contains("\"adaptiveCoreWeight\"");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double sanitizeDouble(double value, double defaultValue, double min, double max) {
        double finiteValue = Double.isFinite(value) ? value : defaultValue;
        return Math.max(min, Math.min(max, finiteValue));
    }

    private static void logCorrection(String key, Object oldValue, Object newValue) {
        LazyChunksMod.LOGGER.warn("Corrected invalid config value {}={} to {}", key, oldValue, newValue);
    }
}
