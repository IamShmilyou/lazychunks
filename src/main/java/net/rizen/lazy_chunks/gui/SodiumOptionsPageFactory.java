package net.rizen.lazy_chunks.gui;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.text.Text;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class SodiumOptionsPageFactory {
    private static final OptionStorage<LazyChunksConfig> STORAGE = new OptionStorage<>() {
        @Override
        public LazyChunksConfig getData() {
            return LazyChunksConfig.getInstance();
        }

        @Override
        public void save() {
            this.getData().save();
        }
    };

    private SodiumOptionsPageFactory() {
    }

    public static OptionPage createPage() {
        return new OptionPage(
                Text.translatable("lazy_chunks.options.page"),
                ImmutableList.of(createGeneralGroup(), createBudgetGroup(), createDynamicCoreGroup())
        );
    }

    private static OptionGroup createGeneralGroup() {
        return OptionGroup.createBuilder()
                .add(booleanOption("enabled", config -> config.lazyChunkLoadingEnabled, (config, value) -> config.lazyChunkLoadingEnabled = value))
                .add(booleanOption("debug_overlay", config -> config.showDebugOverlay, (config, value) -> config.showDebugOverlay = value))
                .add(intOption("target_fps", 30, 240, 1, config -> config.targetFps, (config, value) -> config.targetFps = value, fpsFormatter()))
                .add(intOption("fps_threshold", 30, 240, 1, config -> config.fpsThreshold, (config, value) -> config.fpsThreshold = value, fpsFormatter()))
                .add(booleanOption("proactive_throttling", config -> config.proactiveThrottling, (config, value) -> config.proactiveThrottling = value))
                .add(booleanOption("teleport_protection", config -> config.teleportProtection, (config, value) -> config.teleportProtection = value))
                .build();
    }

    private static OptionGroup createBudgetGroup() {
        return OptionGroup.createBuilder()
                .add(intOption("base_weight", 5, 120, 1, config -> scale(config.baseWeightPerFrame, 10), (config, value) -> config.baseWeightPerFrame = value / 10.0, tenthsFormatter()))
                .add(intOption("max_frame_time", 2, 40, 1, config -> scale(config.maxFrameTimePercent, 2), (config, value) -> config.maxFrameTimePercent = value / 2.0, halfPercentFormatter()))
                .add(intOption("minimum_budget", 26, 120, 1, config -> scale(config.minimumBudget, 10), (config, value) -> config.minimumBudget = value / 10.0, tenthsFormatter()))
                .build();
    }

    private static OptionGroup createDynamicCoreGroup() {
        return OptionGroup.createBuilder()
                .add(booleanOption("dynamic_core_boost", config -> config.dynamicThrottleCoreBoost, (config, value) -> config.dynamicThrottleCoreBoost = value))
                .add(intOption("low_threshold", 20, 120, 1, config -> (int) Math.round(config.dynamicBoostOnePercentLowThreshold), (config, value) -> config.dynamicBoostOnePercentLowThreshold = value, fpsFormatter()))
                .add(intOption("low_duration", 1, 12, 1, config -> (int) Math.round(config.dynamicBoostLowDurationSeconds), (config, value) -> config.dynamicBoostLowDurationSeconds = value, secondsFormatter()))
                .add(intOption("average_fps_threshold", 30, 360, 1, config -> config.dynamicBoostAverageFpsThreshold, (config, value) -> config.dynamicBoostAverageFpsThreshold = value, fpsFormatter()))
                .add(intOption("fps_per_core", 10, 180, 1, config -> config.dynamicBoostFpsPerExtraCore, (config, value) -> config.dynamicBoostFpsPerExtraCore = value, fpsFormatter()))
                .add(intOption("max_extra_cores", 0, 3, 1, config -> config.dynamicBoostMaxExtraCores, (config, value) -> config.dynamicBoostMaxExtraCores = value, ControlValueFormatter.number()))
                .add(intOption("core_increase_step", 1, 3, 1, config -> config.dynamicBoostCoreIncreaseStep, (config, value) -> config.dynamicBoostCoreIncreaseStep = value, ControlValueFormatter.number()))
                .add(intOption("core_decrease_step", 1, 3, 1, config -> config.dynamicBoostCoreDecreaseStep, (config, value) -> config.dynamicBoostCoreDecreaseStep = value, ControlValueFormatter.number()))
                .add(intOption("weight_per_core", 0, 50, 1, config -> scale(config.dynamicBoostWeightPerCore, 10), (config, value) -> config.dynamicBoostWeightPerCore = value / 10.0, tenthsFormatter()))
                .build();
    }

    private static Option<Boolean> booleanOption(String key, Function<LazyChunksConfig, Boolean> getter, BiConsumer<LazyChunksConfig, Boolean> setter) {
        return OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setName(name(key))
                .setTooltip(tooltip(key))
                .setBinding(setter, getter)
                .setControl(option -> new TickBoxControl(option))
                .setImpact(OptionImpact.LOW)
                .build();
    }

    private static Option<Integer> intOption(String key, int min, int max, int interval, Function<LazyChunksConfig, Integer> getter, BiConsumer<LazyChunksConfig, Integer> setter, ControlValueFormatter formatter) {
        return OptionImpl.createBuilder(Integer.class, STORAGE)
                .setName(name(key))
                .setTooltip(tooltip(key))
                .setBinding(setter, getter)
                .setControl(option -> new SliderControl(option, min, max, interval, formatter))
                .setImpact(OptionImpact.LOW)
                .build();
    }

    private static Text name(String key) {
        return Text.translatable("lazy_chunks.option." + key + ".name");
    }

    private static Text tooltip(String key) {
        return Text.translatable("lazy_chunks.option." + key + ".tooltip");
    }

    private static ControlValueFormatter fpsFormatter() {
        return value -> Text.literal(value + " FPS");
    }

    private static ControlValueFormatter secondsFormatter() {
        return value -> Text.literal(value + "s");
    }

    private static ControlValueFormatter tenthsFormatter() {
        return value -> Text.literal(String.format("%.1f", value / 10.0));
    }

    private static ControlValueFormatter halfPercentFormatter() {
        return value -> Text.literal(String.format("%.1f%%", value / 2.0));
    }

    private static int scale(double value, int factor) {
        return (int) Math.round(value * factor);
    }
}
