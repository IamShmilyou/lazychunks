package net.rizen.lazy_chunks.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class LazyChunksConfigScreen extends Screen {
    private static final Text TITLE = Text.literal("LazyChunks");
    private static final int CONTROL_WIDTH = 310;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_SPACING = 20;

    private final Screen parent;
    private LazyChunksConfig config;
    private Page page = Page.GENERAL;

    public LazyChunksConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
        this.config = LazyChunksConfig.getInstance();
    }

    @Override
    protected void init() {
        int x = Math.max(10, this.width / 2 - CONTROL_WIDTH / 2);
        int width = Math.min(CONTROL_WIDTH, this.width - 20);

        int tabWidth = Math.min(75, (this.width - 20) / 4);
        int tabX = this.width / 2 - (tabWidth * 4) / 2;

        ButtonWidget generalTab = ButtonWidget.builder(Text.literal("General"), button -> {
            this.page = Page.GENERAL;
            this.clearAndInit();
        }).dimensions(tabX, 36, tabWidth, 20).build();
        generalTab.active = this.page != Page.GENERAL;
        this.addDrawableChild(generalTab);

        ButtonWidget budgetTab = ButtonWidget.builder(Text.literal("Budget"), button -> {
            this.page = Page.BUDGET;
            this.clearAndInit();
        }).dimensions(tabX + tabWidth, 36, tabWidth, 20).build();
        budgetTab.active = this.page != Page.BUDGET;
        this.addDrawableChild(budgetTab);

        ButtonWidget detectionTab = ButtonWidget.builder(Text.literal("Detect"), button -> {
            this.page = Page.CORE_DETECTION;
            this.clearAndInit();
        }).dimensions(tabX + tabWidth * 2, 36, tabWidth, 20).build();
        detectionTab.active = this.page != Page.CORE_DETECTION;
        this.addDrawableChild(detectionTab);

        ButtonWidget coreTab = ButtonWidget.builder(Text.literal("Core"), button -> {
            this.page = Page.CORE_BUDGET;
            this.clearAndInit();
        }).dimensions(tabX + tabWidth * 3, 36, tabWidth, 20).build();
        coreTab.active = this.page != Page.CORE_BUDGET;
        this.addDrawableChild(coreTab);

        int y = 70;
        if (this.page == Page.GENERAL) {
            y = this.addGeneralControls(x, y, width);
        } else if (this.page == Page.BUDGET) {
            y = this.addBudgetControls(x, y, width);
        } else if (this.page == Page.CORE_DETECTION) {
            y = this.addCoreDetectionControls(x, y, width);
        } else {
            y = this.addCoreBudgetControls(x, y, width);
        }

        int footerY = this.height - 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> {
            this.resetToDefaults();
            this.clearAndInit();
        }).dimensions(this.width / 2 - 155, footerY, 98, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reload"), button -> {
            LazyChunksConfig.reload();
            this.config = LazyChunksConfig.getInstance();
            this.clearAndInit();
        }).dimensions(this.width / 2 - 49, footerY, 98, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                .dimensions(this.width / 2 + 57, footerY, 98, 20)
                .build());
    }

    private int addGeneralControls(int x, int y, int width) {
        y = this.addToggle(x, y, width, "Lazy Chunk Loading", () -> this.config.lazyChunkLoadingEnabled, value -> this.config.lazyChunkLoadingEnabled = value);
        y = this.addToggle(x, y, width, "F3 Debug Info", () -> this.config.showDebugOverlay, value -> this.config.showDebugOverlay = value);
        y = this.addIntSlider(x, y, width, "Target FPS", 30, 240, () -> this.config.targetFps, value -> this.config.targetFps = value);
        y = this.addIntSlider(x, y, width, "FPS Threshold", 30, 240, () -> this.config.fpsThreshold, value -> this.config.fpsThreshold = value);
        y = this.addToggle(x, y, width, "Proactive Throttling", () -> this.config.proactiveThrottling, value -> this.config.proactiveThrottling = value);
        return this.addToggle(x, y, width, "Teleport Protection", () -> this.config.teleportProtection, value -> this.config.teleportProtection = value);
    }

    private int addBudgetControls(int x, int y, int width) {
        y = this.addDoubleSlider(x, y, width, "Base Weight", 0.5, 12.0, 0.1, () -> this.config.baseWeightPerFrame, value -> this.config.baseWeightPerFrame = value);
        y = this.addDoubleSlider(x, y, width, "Max Frame Time", 1.0, 20.0, 0.5, () -> this.config.maxFrameTimePercent, value -> this.config.maxFrameTimePercent = value, "%.1f%%");
        return this.addDoubleSlider(x, y, width, "Minimum Budget", 2.6, 12.0, 0.1, () -> this.config.minimumBudget, value -> this.config.minimumBudget = value);
    }

    private int addCoreDetectionControls(int x, int y, int width) {
        y = this.addToggle(x, y, width, "Dynamic Core Boost", () -> this.config.dynamicThrottleCoreBoost, value -> this.config.dynamicThrottleCoreBoost = value);
        y = this.addDoubleSlider(x, y, width, "1% Low Threshold", 20.0, 120.0, 1.0, () -> this.config.dynamicBoostOnePercentLowThreshold, value -> this.config.dynamicBoostOnePercentLowThreshold = value, "%.0f FPS");
        y = this.addDoubleSlider(x, y, width, "Low Duration", 1.0, 12.0, 1.0, () -> this.config.dynamicBoostLowDurationSeconds, value -> this.config.dynamicBoostLowDurationSeconds = value, "%.0fs");
        y = this.addIntSlider(x, y, width, "Average FPS Threshold", 30, 360, () -> this.config.dynamicBoostAverageFpsThreshold, value -> this.config.dynamicBoostAverageFpsThreshold = value);
        return this.addIntSlider(x, y, width, "FPS Per Core", 10, 180, () -> this.config.dynamicBoostFpsPerExtraCore, value -> this.config.dynamicBoostFpsPerExtraCore = value);
    }

    private int addCoreBudgetControls(int x, int y, int width) {
        y = this.addIntSlider(x, y, width, "Max Extra Cores", 0, 3, () -> this.config.dynamicBoostMaxExtraCores, value -> this.config.dynamicBoostMaxExtraCores = value);
        y = this.addIntSlider(x, y, width, "Core Increase Step", 1, 3, () -> this.config.dynamicBoostCoreIncreaseStep, value -> this.config.dynamicBoostCoreIncreaseStep = value);
        y = this.addIntSlider(x, y, width, "Core Decrease Step", 1, 3, () -> this.config.dynamicBoostCoreDecreaseStep, value -> this.config.dynamicBoostCoreDecreaseStep = value);
        return this.addDoubleSlider(x, y, width, "Weight Per Core", 0.0, 5.0, 0.1, () -> this.config.dynamicBoostWeightPerCore, value -> this.config.dynamicBoostWeightPerCore = value);
    }

    private int addToggle(int x, int y, int width, String label, Supplier<Boolean> getter, BooleanSetter setter) {
        this.addDrawableChild(ButtonWidget.builder(toggleText(label, getter.get()), button -> {
            boolean value = !getter.get();
            setter.accept(value);
            button.setMessage(toggleText(label, value));
        }).dimensions(x, y, width, CONTROL_HEIGHT).build());
        return y + CONTROL_SPACING;
    }

    private int addIntSlider(int x, int y, int width, String label, int min, int max, IntSupplier getter, IntSetter setter) {
        this.addDrawableChild(new ConfigSlider(
                x,
                y,
                width,
                CONTROL_HEIGHT,
                label,
                min,
                max,
                1.0,
                getter::getAsInt,
                value -> setter.accept((int) Math.round(value)),
                "%.0f"
        ));
        return y + CONTROL_SPACING;
    }

    private int addDoubleSlider(int x, int y, int width, String label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter) {
        return this.addDoubleSlider(x, y, width, label, min, max, step, getter, setter, "%.1f");
    }

    private int addDoubleSlider(int x, int y, int width, String label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, String valueFormat) {
        this.addDrawableChild(new ConfigSlider(x, y, width, CONTROL_HEIGHT, label, min, max, step, getter, setter, valueFormat));
        return y + CONTROL_SPACING;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        Text status = SodiumCompat.isSodiumLoaded()
                ? Text.literal("Sodium detected - LazyChunks config is available here")
                : Text.literal("Sodium not detected - editing lazy_chunks.json");
        context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, 62, 0xA0A0A0);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.config.save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void removed() {
        this.config.save();
    }

    private void resetToDefaults() {
        LazyChunksConfig defaults = new LazyChunksConfig();

        this.config.lazyChunkLoadingEnabled = defaults.lazyChunkLoadingEnabled;
        this.config.showDebugOverlay = defaults.showDebugOverlay;
        this.config.targetFps = defaults.targetFps;
        this.config.fpsThreshold = defaults.fpsThreshold;
        this.config.baseWeightPerFrame = defaults.baseWeightPerFrame;
        this.config.maxFrameTimePercent = defaults.maxFrameTimePercent;
        this.config.proactiveThrottling = defaults.proactiveThrottling;
        this.config.teleportProtection = defaults.teleportProtection;
        this.config.minimumBudget = defaults.minimumBudget;
        this.config.dynamicThrottleCoreBoost = defaults.dynamicThrottleCoreBoost;
        this.config.dynamicBoostOnePercentLowThreshold = defaults.dynamicBoostOnePercentLowThreshold;
        this.config.dynamicBoostLowDurationSeconds = defaults.dynamicBoostLowDurationSeconds;
        this.config.dynamicBoostAverageFpsThreshold = defaults.dynamicBoostAverageFpsThreshold;
        this.config.dynamicBoostFpsPerExtraCore = defaults.dynamicBoostFpsPerExtraCore;
        this.config.dynamicBoostMaxExtraCores = defaults.dynamicBoostMaxExtraCores;
        this.config.dynamicBoostCoreDecreaseStep = defaults.dynamicBoostCoreDecreaseStep;
        this.config.dynamicBoostCoreIncreaseStep = defaults.dynamicBoostCoreIncreaseStep;
        this.config.dynamicBoostWeightPerCore = defaults.dynamicBoostWeightPerCore;
        this.config.save();
    }

    private static Text toggleText(String label, boolean value) {
        return Text.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    private enum Page {
        GENERAL,
        BUDGET,
        CORE_DETECTION,
        CORE_BUDGET
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void accept(boolean value);
    }

    @FunctionalInterface
    private interface IntSupplier {
        int getAsInt();
    }

    @FunctionalInterface
    private interface IntSetter {
        void accept(int value);
    }

    private static final class ConfigSlider extends SliderWidget {
        private final String label;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final String valueFormat;

        private ConfigSlider(int x, int y, int width, int height, String label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, String valueFormat) {
            super(x, y, width, height, Text.empty(), normalize(getter.getAsDouble(), min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.valueFormat = valueFormat;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double actualValue = this.roundToStep(this.denormalize(this.value));
            this.setMessage(Text.literal(this.label + ": " + String.format(this.valueFormat, actualValue)));
        }

        @Override
        protected void applyValue() {
            double actualValue = this.roundToStep(this.denormalize(this.value));
            this.setter.accept(actualValue);
            this.value = normalize(this.getter.getAsDouble(), this.min, this.max);
        }

        private double denormalize(double normalizedValue) {
            return this.min + (this.max - this.min) * normalizedValue;
        }

        private double roundToStep(double rawValue) {
            double steppedValue = Math.round(rawValue / this.step) * this.step;
            return Math.max(this.min, Math.min(this.max, steppedValue));
        }

        private static double normalize(double value, double min, double max) {
            if (max <= min) {
                return 0.0;
            }

            return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
        }
    }
}
