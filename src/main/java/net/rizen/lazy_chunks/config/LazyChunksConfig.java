package net.rizen.lazy_chunks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.rizen.lazy_chunks.LazyChunksMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LazyChunksConfig {
    private static LazyChunksConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("lazy_chunks.json");

    /**
     * Enable or disable the Lazy Chunk Loading feature.
     * When enabled, terrain loading is spread across multiple frames to reduce stutters.
     */
    public boolean lazyChunkLoadingEnabled = true;

    /**
     * Show LazyChunks information in the F3 debug overlay.
     */
    public boolean showDebugOverlay = true;

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
     * Enable dynamic budget boost when 1% low FPS stays poor but vanilla FPS still has headroom.
     * This adds virtual throttling cores; it does not create extra threads.
     */
    public boolean dynamicThrottleCoreBoost = true;

    /**
     * 1% low FPS threshold for dynamic boost detection.
     * When 1% low FPS stays below this value long enough, vanilla FPS is checked.
     * Increased from 60 to 45 to prevent triggering too easily and keep 1% low more stable.
     */
    public double dynamicBoostOnePercentLowThreshold = 45.0;

    /**
     * How long 1% low FPS must remain below the threshold before vanilla FPS is checked.
     * Default 10 seconds prevents short dips from raising the chunk budget.
     */
    public double dynamicBoostLowDurationSeconds = 10.0;

    /**
     * Vanilla FPS threshold required before dynamic throttling cores are added.
     * If vanilla FPS is at or below this value, throttling remains unchanged.
     */
    public int dynamicBoostAverageFpsThreshold = 120;

    /**
     * How much vanilla FPS above the threshold is needed for each additional core after the first.
     * Example: threshold 120 and step 60 gives +1 at 121, +2 at 180, +3 at 240.
     */
    public int dynamicBoostFpsPerExtraCore = 60;

    /**
     * Maximum extra virtual throttling cores. Kept in the intended 1-3 range.
     */
    public int dynamicBoostMaxExtraCores = 3;

    /**
     * How many extra virtual throttling cores to remove per check when vanilla FPS falls back
     * to or below the average FPS threshold.
     */
    public int dynamicBoostCoreDecreaseStep = 1;

    /**
     * How many extra virtual throttling cores to add per check when vanilla FPS is above
     * the average FPS threshold.
     */
    public int dynamicBoostCoreIncreaseStep = 1;

    /**
     * Extra chunk weight budget provided by each virtual throttling core.
     * A full chunk packet weighs 1.0.
     */
    public double dynamicBoostWeightPerCore = 1.0;

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
                    config.save();
                    return config;
                }
            } catch (IOException e) {
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
}
