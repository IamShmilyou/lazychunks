package net.rizen.lazy_chunks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.rizen.lazy_chunks.LazyChunksMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LazyChunksConfig {
    private static LazyChunksConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("lazy_chunks.json");
    private static final double HEAVIEST_PACKET_WEIGHT = 2.6D;
    private static final double DEFAULT_BASE_WEIGHT_PER_FRAME = 3.0D;
    private static final double DEFAULT_MAX_FRAME_TIME_PERCENT = 12.0D;
    private static final double DEFAULT_MINIMUM_BUDGET = 3.0D;
    private static final double DEFAULT_ADAPTIVE_LOW_FPS_DURATION_SECONDS = 10.0D;
    private static final double DEFAULT_ADAPTIVE_BOOST_WEIGHT_PER_CORE = 1.0D;
    private static final double MIN_BASE_WEIGHT_PER_FRAME = 0.1D;
    private static final double MIN_MAX_FRAME_TIME_PERCENT = 1.0D;
    private static final double MAX_MAX_FRAME_TIME_PERCENT = 20.0D;
    private static final double MIN_ADAPTIVE_LOW_FPS_DURATION_SECONDS = 10.0D;
    private static final double MAX_ADAPTIVE_LOW_FPS_DURATION_SECONDS = 15.0D;
    private static final double MIN_ADAPTIVE_BOOST_WEIGHT_PER_CORE = 0.1D;
    private static final double MAX_ADAPTIVE_BOOST_WEIGHT_PER_CORE = 10.0D;
    private static final int MIN_TARGET_FPS = 1;
    private static final int MAX_TARGET_FPS = 1000;
    private static final int MIN_FPS_THRESHOLD = 1;
    private static final int MAX_FPS_THRESHOLD = 1000;
    private static final int MIN_ADAPTIVE_FPS_THRESHOLD = 1;
    private static final int MAX_ADAPTIVE_FPS_THRESHOLD = 1000;
    private static final int MIN_ADAPTIVE_BOOST_CORES = 1;
    private static final int MAX_ADAPTIVE_BOOST_CORES = 3;
    private static final int DEFAULT_ADAPTIVE_FPS_PER_BOOST_CORE = 30;

    /**
     * Enable or disable the Lazy Chunk Loading feature.
     * When enabled, terrain loading is spread across multiple frames to reduce stutters.
     */
    public boolean lazyChunkLoadingEnabled = true;

    /**
     * Show LazyChunks metrics in the F3 debug overlay.
     */
    public boolean showDebugOverlayInfo = true;

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
     * Enable adaptive budget boost.
     * When 1% low FPS stays low for several seconds, vanilla FPS is checked.
     * If vanilla FPS has enough headroom, LazyChunks temporarily raises its per-frame chunk budget.
     */
    public boolean adaptiveBudgetBoostEnabled = true;

    /**
     * 1% low FPS threshold used by adaptive budget boost.
     * Adaptive boost starts watching when 1% low FPS stays below this value.
     */
    public int adaptiveLowFpsThreshold = 60;

    /**
     * How long 1% low FPS must stay below adaptiveLowFpsThreshold before checking vanilla FPS.
     * Clamped to 10-15 seconds to avoid reacting to short spikes.
     */
    public double adaptiveLowFpsDurationSeconds = 10.0;

    /**
     * Vanilla FPS threshold required before budget boost is allowed.
     * If vanilla FPS is below or equal to this value, active boost ramps down.
     */
    public int adaptiveAverageFpsThreshold = 120;

    /**
     * Maximum adaptive boost cores, clamped to 1-3.
     * Each core adds adaptiveBoostWeightPerCore to the per-frame chunk budget.
     */
    public int adaptiveMaxBoostCores = 3;

    /**
     * Extra chunk weight added per adaptive boost core.
     */
    public double adaptiveBoostWeightPerCore = 1.0;

    /**
     * Additional vanilla FPS headroom needed for each extra boost core.
     * With the default of 30, FPS 121-149 gives +1, 150-179 gives +2, 180+ gives +3.
     */
    public int adaptiveFpsPerBoostCore = 30;

    public static LazyChunksConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static LazyChunksConfig load() {
        LazyChunksConfig config = null;
        boolean shouldSave = false;

        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                config = GSON.fromJson(json, LazyChunksConfig.class);
                if (config != null) {
                    LazyChunksMod.LOGGER.info("Loaded config from {}", CONFIG_PATH);
                    if (!json.contains("\"adaptiveBudgetBoostEnabled\"")
                            || !json.contains("\"showDebugOverlayInfo\"")) {
                        shouldSave = true;
                    }
                } else {
                    LazyChunksMod.LOGGER.warn("Config file {} was empty, falling back to defaults", CONFIG_PATH);
                    shouldSave = true;
                }
            } catch (IOException e) {
                LazyChunksMod.LOGGER.error("Failed to load config", e);
                shouldSave = true;
            } catch (RuntimeException e) {
                LazyChunksMod.LOGGER.warn("Config file {} is invalid, regenerating defaults", CONFIG_PATH, e);
                shouldSave = true;
            }
        } else {
            shouldSave = true;
        }

        if (config == null) {
            config = new LazyChunksConfig();
        }

        if (config.normalize()) {
            LazyChunksMod.LOGGER.warn("Config file {} contained invalid values and was normalized", CONFIG_PATH);
            shouldSave = true;
        }

        if (shouldSave) {
            config.save();
        }
        return config;
    }

    public void save() {
        Path tempFile = null;
        try {
            if (normalize()) {
                LazyChunksMod.LOGGER.warn("Config file {} contained invalid values and was normalized before saving", CONFIG_PATH);
            }
            Files.createDirectories(CONFIG_PATH.getParent());
            tempFile = Files.createTempFile(CONFIG_PATH.getParent(), "lazy_chunks", ".json.tmp");
            Files.writeString(tempFile, GSON.toJson(this));
            try {
                Files.move(tempFile, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveError) {
                Files.move(tempFile, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile = null;
            LazyChunksMod.LOGGER.info("Saved config to {}", CONFIG_PATH);
        } catch (IOException e) {
            LazyChunksMod.LOGGER.error("Failed to save config", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static void reload() {
        INSTANCE = load();
        LazyChunksMod.LOGGER.info("Config reloaded");
    }

    private boolean normalize() {
        boolean changed = false;

        if (targetFps < MIN_TARGET_FPS) {
            targetFps = MIN_TARGET_FPS;
            changed = true;
        } else if (targetFps > MAX_TARGET_FPS) {
            targetFps = MAX_TARGET_FPS;
            changed = true;
        }

        if (fpsThreshold < MIN_FPS_THRESHOLD) {
            fpsThreshold = MIN_FPS_THRESHOLD;
            changed = true;
        } else if (fpsThreshold > MAX_FPS_THRESHOLD) {
            fpsThreshold = MAX_FPS_THRESHOLD;
            changed = true;
        }

        if (!Double.isFinite(baseWeightPerFrame)) {
            baseWeightPerFrame = DEFAULT_BASE_WEIGHT_PER_FRAME;
            changed = true;
        } else if (baseWeightPerFrame < MIN_BASE_WEIGHT_PER_FRAME) {
            baseWeightPerFrame = MIN_BASE_WEIGHT_PER_FRAME;
            changed = true;
        }

        if (!Double.isFinite(maxFrameTimePercent)) {
            maxFrameTimePercent = DEFAULT_MAX_FRAME_TIME_PERCENT;
            changed = true;
        } else {
            if (maxFrameTimePercent < MIN_MAX_FRAME_TIME_PERCENT) {
                maxFrameTimePercent = MIN_MAX_FRAME_TIME_PERCENT;
                changed = true;
            } else if (maxFrameTimePercent > MAX_MAX_FRAME_TIME_PERCENT) {
                maxFrameTimePercent = MAX_MAX_FRAME_TIME_PERCENT;
                changed = true;
            }
        }

        if (!Double.isFinite(minimumBudget)) {
            minimumBudget = DEFAULT_MINIMUM_BUDGET;
            changed = true;
        } else if (minimumBudget < HEAVIEST_PACKET_WEIGHT) {
            minimumBudget = HEAVIEST_PACKET_WEIGHT;
            changed = true;
        }

        if (adaptiveLowFpsThreshold < MIN_ADAPTIVE_FPS_THRESHOLD) {
            adaptiveLowFpsThreshold = MIN_ADAPTIVE_FPS_THRESHOLD;
            changed = true;
        } else if (adaptiveLowFpsThreshold > MAX_ADAPTIVE_FPS_THRESHOLD) {
            adaptiveLowFpsThreshold = MAX_ADAPTIVE_FPS_THRESHOLD;
            changed = true;
        }

        if (!Double.isFinite(adaptiveLowFpsDurationSeconds)) {
            adaptiveLowFpsDurationSeconds = DEFAULT_ADAPTIVE_LOW_FPS_DURATION_SECONDS;
            changed = true;
        } else if (adaptiveLowFpsDurationSeconds < MIN_ADAPTIVE_LOW_FPS_DURATION_SECONDS) {
            adaptiveLowFpsDurationSeconds = MIN_ADAPTIVE_LOW_FPS_DURATION_SECONDS;
            changed = true;
        } else if (adaptiveLowFpsDurationSeconds > MAX_ADAPTIVE_LOW_FPS_DURATION_SECONDS) {
            adaptiveLowFpsDurationSeconds = MAX_ADAPTIVE_LOW_FPS_DURATION_SECONDS;
            changed = true;
        }

        if (adaptiveAverageFpsThreshold < MIN_ADAPTIVE_FPS_THRESHOLD) {
            adaptiveAverageFpsThreshold = MIN_ADAPTIVE_FPS_THRESHOLD;
            changed = true;
        } else if (adaptiveAverageFpsThreshold > MAX_ADAPTIVE_FPS_THRESHOLD) {
            adaptiveAverageFpsThreshold = MAX_ADAPTIVE_FPS_THRESHOLD;
            changed = true;
        }

        if (adaptiveMaxBoostCores < MIN_ADAPTIVE_BOOST_CORES) {
            adaptiveMaxBoostCores = MIN_ADAPTIVE_BOOST_CORES;
            changed = true;
        } else if (adaptiveMaxBoostCores > MAX_ADAPTIVE_BOOST_CORES) {
            adaptiveMaxBoostCores = MAX_ADAPTIVE_BOOST_CORES;
            changed = true;
        }

        if (!Double.isFinite(adaptiveBoostWeightPerCore)) {
            adaptiveBoostWeightPerCore = DEFAULT_ADAPTIVE_BOOST_WEIGHT_PER_CORE;
            changed = true;
        } else if (adaptiveBoostWeightPerCore < MIN_ADAPTIVE_BOOST_WEIGHT_PER_CORE) {
            adaptiveBoostWeightPerCore = MIN_ADAPTIVE_BOOST_WEIGHT_PER_CORE;
            changed = true;
        } else if (adaptiveBoostWeightPerCore > MAX_ADAPTIVE_BOOST_WEIGHT_PER_CORE) {
            adaptiveBoostWeightPerCore = MAX_ADAPTIVE_BOOST_WEIGHT_PER_CORE;
            changed = true;
        }

        if (adaptiveFpsPerBoostCore < 1) {
            adaptiveFpsPerBoostCore = DEFAULT_ADAPTIVE_FPS_PER_BOOST_CORE;
            changed = true;
        } else if (adaptiveFpsPerBoostCore > MAX_ADAPTIVE_FPS_THRESHOLD) {
            adaptiveFpsPerBoostCore = MAX_ADAPTIVE_FPS_THRESHOLD;
            changed = true;
        }

        return changed;
    }
}
