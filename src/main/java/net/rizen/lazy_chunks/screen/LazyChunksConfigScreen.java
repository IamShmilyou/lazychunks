package net.rizen.lazy_chunks.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

public class LazyChunksConfigScreen extends OptionsSubScreen {
    private final LazyChunksConfig config;

    public LazyChunksConfigScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, Component.translatable("lazy_chunks.options.title"));
        this.config = LazyChunksConfig.getInstance();
    }

    @Override
    protected void addOptions() {
        this.list.addHeader(text("group.general"));
        this.list.addSmall(
                booleanButton(
                        "lazy_chunk_loading",
                        this.config.lazyChunkLoadingEnabled,
                        value -> this.config.lazyChunkLoadingEnabled = value
                ),
                booleanButton(
                        "debug_info",
                        this.config.debugInfoEnabled,
                        value -> this.config.debugInfoEnabled = value
                )
        );
        this.list.addSmall(
                intButton(
                        "target_fps",
                        this.config.targetFps,
                        valuesIncluding(this.config.targetFps, 1, 240, 5, 300, 360, 500, 1000),
                        value -> Component.translatable("lazy_chunks.options.value.fps", value),
                        value -> this.config.targetFps = value
                ),
                intButton(
                        "fps_threshold",
                        this.config.fpsThreshold,
                        valuesIncluding(this.config.fpsThreshold, 1, 240, 5, 300, 360, 500, 1000),
                        value -> Component.translatable("lazy_chunks.options.value.fps", value),
                        value -> this.config.fpsThreshold = value
                )
        );
        this.list.addSmall(
                scaledDoubleButton(
                        "base_weight",
                        this.config.baseWeightPerFrame,
                        1,
                        100,
                        1,
                        value -> this.config.baseWeightPerFrame = value
                ),
                scaledDoubleButton(
                        "frame_time_percent",
                        this.config.maxFrameTimePercent,
                        10,
                        200,
                        5,
                        value -> this.config.maxFrameTimePercent = value,
                        value -> Component.translatable("lazy_chunks.options.value.percent", String.format(Locale.ROOT, "%.1f", value / 10.0D))
                )
        );
        this.list.addSmall(
                booleanButton(
                        "proactive_throttling",
                        this.config.proactiveThrottling,
                        value -> this.config.proactiveThrottling = value
                ),
                booleanButton(
                        "teleport_protection",
                        this.config.teleportProtection,
                        value -> this.config.teleportProtection = value
                )
        );
        this.list.addSmall(
                scaledDoubleButton(
                        "minimum_budget",
                        this.config.minimumBudget,
                        26,
                        100,
                        1,
                        value -> this.config.minimumBudget = value
                ),
                null
        );

        this.list.addHeader(text("group.adaptive_core"));
        this.list.addSmall(
                booleanButton(
                        "adaptive_core",
                        this.config.adaptiveCoreBoostEnabled,
                        value -> this.config.adaptiveCoreBoostEnabled = value
                ),
                intButton(
                        "low_fps_duration",
                        this.config.adaptiveLowFpsDurationSeconds,
                        valuesIncluding(this.config.adaptiveLowFpsDurationSeconds, 10, 12, 1),
                        value -> Component.translatable("lazy_chunks.options.value.seconds", value),
                        value -> this.config.adaptiveLowFpsDurationSeconds = value
                )
        );
        this.list.addSmall(
                intButton(
                        "low_fps_threshold",
                        this.config.adaptiveLowFpsThreshold,
                        valuesIncluding(this.config.adaptiveLowFpsThreshold, 1, 240, 5, 300, 360, 500, 1000),
                        value -> Component.translatable("lazy_chunks.options.value.fps", value),
                        value -> this.config.adaptiveLowFpsThreshold = value
                ),
                intButton(
                        "average_fps_threshold",
                        this.config.adaptiveAverageFpsThreshold,
                        valuesIncluding(this.config.adaptiveAverageFpsThreshold, 1, 240, 5, 300, 360, 500, 1000),
                        value -> Component.translatable("lazy_chunks.options.value.fps", value),
                        value -> this.config.adaptiveAverageFpsThreshold = value
                )
        );
        this.list.addSmall(
                intButton(
                        "core_max",
                        this.config.adaptiveCoreBoostMax,
                        valuesIncluding(this.config.adaptiveCoreBoostMax, 1, 3, 1),
                        value -> Component.literal(Integer.toString(value)),
                        value -> this.config.adaptiveCoreBoostMax = value
                ),
                scaledDoubleButton(
                        "core_weight",
                        this.config.adaptiveCoreBoostWeight,
                        1,
                        100,
                        1,
                        value -> this.config.adaptiveCoreBoostWeight = value
                )
        );
    }

    @Override
    public void removed() {
        this.config.save();
        super.removed();
    }

    private static AbstractWidget booleanButton(String key, boolean currentValue, Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(currentValue)
                .withTooltip(value -> Tooltip.create(tooltip(key)))
                .create(0, 0, 150, 20, text(key), (button, value) -> setter.accept(value));
    }

    private static AbstractWidget intButton(
            String key,
            int currentValue,
            List<Integer> values,
            Function<Integer, Component> formatter,
            IntConsumer setter
    ) {
        return CycleButton.builder(formatter::apply, currentValue)
                .withValues(values)
                .withTooltip(value -> Tooltip.create(tooltip(key)))
                .create(0, 0, 150, 20, text(key), (button, value) -> setter.accept(value));
    }

    private static AbstractWidget scaledDoubleButton(
            String key,
            double currentValue,
            int minTenths,
            int maxTenths,
            int stepTenths,
            Consumer<Double> setter
    ) {
        return scaledDoubleButton(
                key,
                currentValue,
                minTenths,
                maxTenths,
                stepTenths,
                setter,
                value -> Component.literal(String.format(Locale.ROOT, "%.1f", value / 10.0D))
        );
    }

    private static AbstractWidget scaledDoubleButton(
            String key,
            double currentValue,
            int minTenths,
            int maxTenths,
            int stepTenths,
            Consumer<Double> setter,
            Function<Integer, Component> formatter
    ) {
        int currentTenths = (int) Math.round(currentValue * 10.0D);
        return intButton(
                key,
                currentTenths,
                valuesIncluding(currentTenths, minTenths, maxTenths, stepTenths),
                formatter,
                value -> setter.accept(value / 10.0D)
        );
    }

    private static Component text(String key) {
        return Component.translatable("lazy_chunks.options." + key);
    }

    private static Component tooltip(String key) {
        return text(key + ".tooltip");
    }

    private static List<Integer> valuesIncluding(int currentValue, int min, int max, int step, int... extraValues) {
        List<Integer> values = new ArrayList<>();

        for (int value = min; value <= max; value += step) {
            values.add(value);
        }

        for (int value : extraValues) {
            values.add(value);
        }

        values.add(currentValue);
        values.removeIf(value -> value < min);
        Collections.sort(values);

        List<Integer> uniqueValues = new ArrayList<>();
        Integer previousValue = null;
        for (Integer value : values) {
            if (!value.equals(previousValue)) {
                uniqueValues.add(value);
            }
            previousValue = value;
        }

        return uniqueValues;
    }
}
