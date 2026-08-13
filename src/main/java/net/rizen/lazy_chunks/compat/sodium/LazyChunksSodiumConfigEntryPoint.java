package net.rizen.lazy_chunks.compat.sodium;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.Locale;

public class LazyChunksSodiumConfigEntryPoint implements ConfigEntryPoint {
    private static final Identifier SODIUM_ICON =
            Identifier.fromNamespaceAndPath(LazyChunksMod.MOD_ID, "icon.png");

    private static final ControlValueFormatter INTEGER = value -> Component.literal(Integer.toString(value));
    private static final ControlValueFormatter FPS = value -> Component.translatable("lazy_chunks.options.value.fps", value);
    private static final ControlValueFormatter SECONDS = value -> Component.translatable("lazy_chunks.options.value.seconds", value);
    private static final ControlValueFormatter DECIMAL = value -> Component.literal(formatTenths(value));
    private static final ControlValueFormatter PERCENT = value -> Component.translatable(
            "lazy_chunks.options.value.percent",
            formatTenths(value)
    );

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        StorageEventHandler storage = config::save;

        builder.registerOwnModOptions()
                .setName("LazyChunks")
                .setVersion(LazyChunksMod.VERSION)
                .setNonTintedIcon(SODIUM_ICON)
                .addPage(builder.createOptionPage()
                        .setName(text("page"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(text("group.general"))
                                .addOption(builder.createBooleanOption(id("lazy_chunk_loading"))
                                        .setName(text("lazy_chunk_loading"))
                                        .setTooltip(tooltip("lazy_chunk_loading"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.lazyChunkLoadingEnabled = value,
                                                () -> config.lazyChunkLoadingEnabled)
                                        .setDefaultValue(true))
                                .addOption(builder.createBooleanOption(id("debug_info"))
                                        .setName(text("debug_info"))
                                        .setTooltip(tooltip("debug_info"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.debugInfoEnabled = value,
                                                () -> config.debugInfoEnabled)
                                        .setDefaultValue(true))
                                .addOption(builder.createIntegerOption(id("target_fps"))
                                        .setName(text("target_fps"))
                                        .setTooltip(tooltip("target_fps"))
                                        .setImpact(OptionImpact.LOW)
                                        .setRange(1, 1000, 1)
                                        .setValueFormatter(FPS)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.targetFps = value, () -> config.targetFps)
                                        .setDefaultValue(60))
                                .addOption(builder.createIntegerOption(id("fps_threshold"))
                                        .setName(text("fps_threshold"))
                                        .setTooltip(tooltip("fps_threshold"))
                                        .setImpact(OptionImpact.LOW)
                                        .setRange(1, 1000, 1)
                                        .setValueFormatter(FPS)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.fpsThreshold = value, () -> config.fpsThreshold)
                                        .setDefaultValue(80))
                                .addOption(builder.createIntegerOption(id("base_weight"))
                                        .setName(text("base_weight"))
                                        .setTooltip(tooltip("base_weight"))
                                        .setImpact(OptionImpact.VARIES)
                                        .setRange(1, 100, 1)
                                        .setValueFormatter(DECIMAL)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.baseWeightPerFrame = value / 10.0D,
                                                () -> tenths(config.baseWeightPerFrame))
                                        .setDefaultValue(30))
                                .addOption(builder.createIntegerOption(id("frame_time_percent"))
                                        .setName(text("frame_time_percent"))
                                        .setTooltip(tooltip("frame_time_percent"))
                                        .setImpact(OptionImpact.VARIES)
                                        .setRange(10, 200, 5)
                                        .setValueFormatter(PERCENT)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.maxFrameTimePercent = value / 10.0D,
                                                () -> tenths(config.maxFrameTimePercent))
                                        .setDefaultValue(120))
                                .addOption(builder.createBooleanOption(id("proactive_throttling"))
                                        .setName(text("proactive_throttling"))
                                        .setTooltip(tooltip("proactive_throttling"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.proactiveThrottling = value,
                                                () -> config.proactiveThrottling)
                                        .setDefaultValue(true))
                                .addOption(builder.createBooleanOption(id("teleport_protection"))
                                        .setName(text("teleport_protection"))
                                        .setTooltip(tooltip("teleport_protection"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.teleportProtection = value,
                                                () -> config.teleportProtection)
                                        .setDefaultValue(true))
                                .addOption(builder.createIntegerOption(id("minimum_budget"))
                                        .setName(text("minimum_budget"))
                                        .setTooltip(tooltip("minimum_budget"))
                                        .setImpact(OptionImpact.VARIES)
                                        .setRange(26, 100, 1)
                                        .setValueFormatter(DECIMAL)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.minimumBudget = value / 10.0D,
                                                () -> tenths(config.minimumBudget))
                                        .setDefaultValue(30)))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(text("group.adaptive_core"))
                                .addOption(builder.createBooleanOption(id("adaptive_core"))
                                        .setName(text("adaptive_core"))
                                        .setTooltip(tooltip("adaptive_core"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.adaptiveCoreBoostEnabled = value,
                                                () -> config.adaptiveCoreBoostEnabled)
                                        .setDefaultValue(true))
                                .addOption(builder.createIntegerOption(id("low_fps_duration"))
                                        .setName(text("low_fps_duration"))
                                        .setTooltip(tooltip("low_fps_duration"))
                                        .setImpact(OptionImpact.LOW)
                                        .setRange(10, 12, 1)
                                        .setValueFormatter(SECONDS)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.adaptiveLowFpsDurationSeconds = value,
                                                () -> config.adaptiveLowFpsDurationSeconds)
                                        .setDefaultValue(10))
                                .addOption(builder.createIntegerOption(id("low_fps_threshold"))
                                        .setName(text("low_fps_threshold"))
                                        .setTooltip(tooltip("low_fps_threshold"))
                                        .setImpact(OptionImpact.LOW)
                                        .setRange(1, 1000, 1)
                                        .setValueFormatter(FPS)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.adaptiveLowFpsThreshold = value,
                                                () -> config.adaptiveLowFpsThreshold)
                                        .setDefaultValue(60))
                                .addOption(builder.createIntegerOption(id("average_fps_threshold"))
                                        .setName(text("average_fps_threshold"))
                                        .setTooltip(tooltip("average_fps_threshold"))
                                        .setImpact(OptionImpact.LOW)
                                        .setRange(1, 1000, 1)
                                        .setValueFormatter(FPS)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.adaptiveAverageFpsThreshold = value,
                                                () -> config.adaptiveAverageFpsThreshold)
                                        .setDefaultValue(120))
                                .addOption(builder.createIntegerOption(id("core_max"))
                                        .setName(text("core_max"))
                                        .setTooltip(tooltip("core_max"))
                                        .setImpact(OptionImpact.LOW)
                                        .setRange(1, 3, 1)
                                        .setValueFormatter(INTEGER)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.adaptiveCoreBoostMax = value,
                                                () -> config.adaptiveCoreBoostMax)
                                        .setDefaultValue(3))
                                .addOption(builder.createIntegerOption(id("core_weight"))
                                        .setName(text("core_weight"))
                                        .setTooltip(tooltip("core_weight"))
                                        .setImpact(OptionImpact.VARIES)
                                        .setRange(1, 100, 1)
                                        .setValueFormatter(DECIMAL)
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.adaptiveCoreBoostWeight = value / 10.0D,
                                                () -> tenths(config.adaptiveCoreBoostWeight))
                                        .setDefaultValue(10))));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LazyChunksMod.MOD_ID, path);
    }

    private static Component text(String key) {
        return Component.translatable("lazy_chunks.options." + key);
    }

    private static Component tooltip(String key) {
        return text(key + ".tooltip");
    }

    private static int tenths(double value) {
        return (int) Math.round(value * 10.0D);
    }

    private static String formatTenths(int value) {
        return String.format(Locale.ROOT, "%.1f", value / 10.0D);
    }
}
