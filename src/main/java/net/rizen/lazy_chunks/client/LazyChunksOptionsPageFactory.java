package net.rizen.lazy_chunks.client;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;

final class LazyChunksOptionsPageFactory {
    static final Component PAGE_NAME = Component.translatable("lazy_chunks.screen.title");

    private static final OptionStorage<LazyChunksConfig> STORAGE = new OptionStorage<>() {
        @Override
        public LazyChunksConfig getData() {
            return LazyChunksConfig.getInstance();
        }

        @Override
        public void save() {
            LazyChunksConfig.getInstance().save();
        }
    };

    private LazyChunksOptionsPageFactory() {
    }

    static ImmutableList<OptionGroup> buildGroups(OptionGroup.Builder groupBuilder) {
        groupBuilder.add(booleanOption("lazy_chunk_loading",
                config -> config.lazyChunkLoadingEnabled,
                (config, value) -> config.lazyChunkLoadingEnabled = value));
        groupBuilder.add(intOption("target_fps",
                30, 240, 5,
                config -> config.targetFps,
                (config, value) -> config.targetFps = value,
                value -> Component.translatable("lazy_chunks.value.fps", value)));
        groupBuilder.add(intOption("fps_threshold",
                30, 240, 5,
                config -> config.fpsThreshold,
                (config, value) -> config.fpsThreshold = value,
                value -> Component.translatable("lazy_chunks.value.fps", value)));
        groupBuilder.add(intOption("base_weight",
                5, 100, 5,
                config -> (int) Math.round(config.baseWeightPerFrame * 10.0),
                (config, value) -> config.baseWeightPerFrame = value / 10.0,
                value -> Component.literal(String.format("%.1f", value / 10.0))));
        groupBuilder.add(intOption("max_frame_time",
                1, 25, 1,
                config -> (int) Math.round(config.maxFrameTimePercent),
                (config, value) -> config.maxFrameTimePercent = value,
                value -> Component.translatable("lazy_chunks.value.percent", value)));
        groupBuilder.add(booleanOption("proactive_throttling",
                config -> config.proactiveThrottling,
                (config, value) -> config.proactiveThrottling = value));
        groupBuilder.add(booleanOption("teleport_protection",
                config -> config.teleportProtection,
                (config, value) -> config.teleportProtection = value));
        groupBuilder.add(intOption("minimum_budget",
                26, 100, 1,
                config -> (int) Math.round(config.minimumBudget * 10.0),
                (config, value) -> config.minimumBudget = value / 10.0,
                value -> Component.literal(String.format("%.1f", value / 10.0))));
        groupBuilder.add(booleanOption("dynamic_core",
                config -> config.dynamicThrottleCoreBoost,
                (config, value) -> config.dynamicThrottleCoreBoost = value));
        groupBuilder.add(intOption("dynamic_low_threshold",
                15, 240, 5,
                config -> (int) Math.round(config.dynamicBoostOnePercentLowThreshold),
                (config, value) -> config.dynamicBoostOnePercentLowThreshold = value,
                value -> Component.translatable("lazy_chunks.value.fps", value)));
        groupBuilder.add(intOption("dynamic_low_duration",
                1, 60, 1,
                config -> (int) Math.round(config.dynamicBoostLowDurationSeconds),
                (config, value) -> config.dynamicBoostLowDurationSeconds = value,
                value -> Component.translatable("lazy_chunks.value.seconds", value)));
        groupBuilder.add(intOption("dynamic_average_fps_threshold",
                30, 360, 10,
                config -> config.dynamicBoostAverageFpsThreshold,
                (config, value) -> config.dynamicBoostAverageFpsThreshold = value,
                value -> Component.translatable("lazy_chunks.value.fps", value)));
        groupBuilder.add(intOption("dynamic_fps_per_core",
                10, 240, 10,
                config -> config.dynamicBoostFpsPerExtraCore,
                (config, value) -> config.dynamicBoostFpsPerExtraCore = value,
                value -> Component.translatable("lazy_chunks.value.fps", value)));
        groupBuilder.add(intOption("dynamic_max_extra_cores",
                0, 3, 1,
                config -> config.dynamicBoostMaxExtraCores,
                (config, value) -> config.dynamicBoostMaxExtraCores = value,
                value -> Component.literal(Integer.toString(value))));
        groupBuilder.add(intOption("dynamic_core_decrease_step",
                1, 3, 1,
                config -> config.dynamicBoostCoreDecreaseStep,
                (config, value) -> config.dynamicBoostCoreDecreaseStep = value,
                value -> Component.literal(Integer.toString(value))));
        groupBuilder.add(intOption("dynamic_weight_per_core",
                0, 50, 1,
                config -> (int) Math.round(config.dynamicBoostWeightPerCore * 10.0),
                (config, value) -> config.dynamicBoostWeightPerCore = value / 10.0,
                value -> Component.literal(String.format("%.1f", value / 10.0))));
        groupBuilder.add(booleanOption("show_debug_overlay",
                config -> config.showDebugOverlay,
                (config, value) -> config.showDebugOverlay = value));

        return ImmutableList.of(groupBuilder.build());
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation(LazyChunksMod.MOD_ID, path);
    }

    private static Option<Boolean> booleanOption(String path,
                                                 Function<LazyChunksConfig, Boolean> getter,
                                                 BiConsumer<LazyChunksConfig, Boolean> setter) {
        return OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id(path))
                .setName(optionName(path))
                .setTooltip(optionTooltip(path))
                .setBinding(setter, getter)
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.LOW)
                .build();
    }

    private static Option<Integer> intOption(String path,
                                             int min, int max, int step,
                                             Function<LazyChunksConfig, Integer> getter,
                                             BiConsumer<LazyChunksConfig, Integer> setter,
                                             IntFunction<Component> formatter) {
        return OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id(path))
                .setName(optionName(path))
                .setTooltip(optionTooltip(path))
                .setBinding(setter, getter)
                .setControl(option -> new SliderControl(option, min, max, step, valueFormatter(formatter)))
                .setImpact(OptionImpact.MEDIUM)
                .build();
    }

    private static Component optionName(String path) {
        return Component.translatable("lazy_chunks.option." + path + ".name");
    }

    private static Component optionTooltip(String path) {
        return Component.translatable("lazy_chunks.option." + path + ".tooltip");
    }

    private static ControlValueFormatter valueFormatter(IntFunction<Component> formatter) {
        return formatter::apply;
    }
}
