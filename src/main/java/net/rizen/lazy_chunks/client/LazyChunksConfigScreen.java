package net.rizen.lazy_chunks.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class LazyChunksConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 310;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 24;

    private final Screen parent;
    private Tab activeTab = Tab.GENERAL;

    public LazyChunksConfigScreen(Screen parent) {
        super(Component.translatable("lazy_chunks.screen.title"));
        this.parent = parent;
    }

    public static Screen create(Screen parent) {
        return new LazyChunksConfigScreen(parent);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int tabY = 30;

        for (Tab tab : Tab.values()) {
            addTabButton(tab, centerX - 155 + tab.ordinal() * 105, tabY);
        }

        int y = 58;
        switch (this.activeTab) {
            case GENERAL -> addGeneralOptions(centerX - PANEL_WIDTH / 2, y);
            case DYNAMIC -> addDynamicOptions(centerX - PANEL_WIDTH / 2, y);
            case DEBUG -> addDebugOptions(centerX - PANEL_WIDTH / 2, y);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> close())
                .bounds(centerX - 100, this.height - 27, 200, 20)
                .build());
    }

    private void addTabButton(Tab tab, int x, int y) {
        Button button = Button.builder(Component.translatable(tab.titleKey), ignored -> {
                    this.activeTab = tab;
                    this.rebuildWidgets();
                })
                .bounds(x, y, 150, 20)
                .width(100)
                .build();
        button.active = this.activeTab != tab;
        this.addRenderableWidget(button);
    }

    private void addGeneralOptions(int x, int y) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        addBooleanOption(x, y, "lazy_chunks.option.lazy_chunk_loading.name", config.lazyChunkLoadingEnabled,
                value -> config.lazyChunkLoadingEnabled = value);
        y += ROW_GAP;
        addIntSlider(x, y, "lazy_chunks.option.target_fps.name", config.targetFps, 30, 240, 5,
                value -> config.targetFps = (int) Math.round(value),
                value -> Component.translatable("lazy_chunks.value.fps", (int) Math.round(value)));
        y += ROW_GAP;
        addIntSlider(x, y, "lazy_chunks.option.fps_threshold.name", config.fpsThreshold, 30, 240, 5,
                value -> config.fpsThreshold = (int) Math.round(value),
                value -> Component.translatable("lazy_chunks.value.fps", (int) Math.round(value)));
        y += ROW_GAP;
        addDoubleSlider(x, y, "lazy_chunks.option.base_weight.name", config.baseWeightPerFrame, 0.5, 10.0, 0.5,
                value -> config.baseWeightPerFrame = value,
                value -> Component.literal(String.format("%.1f", value)));
        y += ROW_GAP;
        addDoubleSlider(x, y, "lazy_chunks.option.max_frame_time.name", config.maxFrameTimePercent, 1.0, 25.0, 1.0,
                value -> config.maxFrameTimePercent = value,
                value -> Component.translatable("lazy_chunks.value.percent", (int) Math.round(value)));
        y += ROW_GAP;
        addBooleanOption(x, y, "lazy_chunks.option.proactive_throttling.name", config.proactiveThrottling,
                value -> config.proactiveThrottling = value);
        y += ROW_GAP;
        addBooleanOption(x, y, "lazy_chunks.option.teleport_protection.name", config.teleportProtection,
                value -> config.teleportProtection = value);
        y += ROW_GAP;
        addDoubleSlider(x, y, "lazy_chunks.option.minimum_budget.name", config.minimumBudget, 2.6, 10.0, 0.1,
                value -> config.minimumBudget = value,
                value -> Component.literal(String.format("%.1f", value)));
    }

    private void addDynamicOptions(int x, int y) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        addBooleanOption(x, y, "lazy_chunks.option.dynamic_core.name", config.dynamicThrottleCoreBoost,
                value -> config.dynamicThrottleCoreBoost = value);
        y += ROW_GAP;
        addDoubleSlider(x, y, "lazy_chunks.option.dynamic_low_threshold.name", config.dynamicBoostOnePercentLowThreshold, 15.0, 240.0, 5.0,
                value -> config.dynamicBoostOnePercentLowThreshold = value,
                value -> Component.translatable("lazy_chunks.value.fps", (int) Math.round(value)));
        y += ROW_GAP;
        addDoubleSlider(x, y, "lazy_chunks.option.dynamic_low_duration.name", config.dynamicBoostLowDurationSeconds, 1.0, 60.0, 1.0,
                value -> config.dynamicBoostLowDurationSeconds = value,
                value -> Component.translatable("lazy_chunks.value.seconds", (int) Math.round(value)));
        y += ROW_GAP;
        addIntSlider(x, y, "lazy_chunks.option.dynamic_average_fps_threshold.name", config.dynamicBoostAverageFpsThreshold, 30, 360, 10,
                value -> config.dynamicBoostAverageFpsThreshold = (int) Math.round(value),
                value -> Component.translatable("lazy_chunks.value.fps", (int) Math.round(value)));
        y += ROW_GAP;
        addIntSlider(x, y, "lazy_chunks.option.dynamic_fps_per_core.name", config.dynamicBoostFpsPerExtraCore, 10, 240, 10,
                value -> config.dynamicBoostFpsPerExtraCore = (int) Math.round(value),
                value -> Component.translatable("lazy_chunks.value.fps", (int) Math.round(value)));
        y += ROW_GAP;
        addIntSlider(x, y, "lazy_chunks.option.dynamic_max_extra_cores.name", config.dynamicBoostMaxExtraCores, 0, 3, 1,
                value -> config.dynamicBoostMaxExtraCores = (int) Math.round(value),
                value -> Component.literal(Integer.toString((int) Math.round(value))));
        y += ROW_GAP;
        addIntSlider(x, y, "lazy_chunks.option.dynamic_core_decrease_step.name", config.dynamicBoostCoreDecreaseStep, 1, 3, 1,
                value -> config.dynamicBoostCoreDecreaseStep = (int) Math.round(value),
                value -> Component.literal(Integer.toString((int) Math.round(value))));
        y += ROW_GAP;
        addDoubleSlider(x, y, "lazy_chunks.option.dynamic_weight_per_core.name", config.dynamicBoostWeightPerCore, 0.0, 5.0, 0.1,
                value -> config.dynamicBoostWeightPerCore = value,
                value -> Component.literal(String.format("%.1f", value)));
    }

    private void addDebugOptions(int x, int y) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        addBooleanOption(x, y, "lazy_chunks.option.show_debug_overlay.name", config.showDebugOverlay,
                value -> config.showDebugOverlay = value);
    }

    private void addBooleanOption(int x, int y, String labelKey, boolean value, BooleanConsumer setter) {
        this.addRenderableWidget(CycleButton.onOffBuilder(value)
                .create(x, y, PANEL_WIDTH, ROW_HEIGHT, Component.translatable(labelKey),
                        (button, selected) -> setter.accept(selected)));
    }

    private void addIntSlider(int x, int y, String labelKey, int value, int min, int max, int step,
                              DoubleConsumer setter, DoubleFunction<Component> formatter) {
        this.addRenderableWidget(new ConfigSlider(x, y, PANEL_WIDTH, ROW_HEIGHT, labelKey, value, min, max, step, setter, formatter));
    }

    private void addDoubleSlider(int x, int y, String labelKey, double value, double min, double max, double step,
                                 DoubleConsumer setter, DoubleFunction<Component> formatter) {
        this.addRenderableWidget(new ConfigSlider(x, y, PANEL_WIDTH, ROW_HEIGHT, labelKey, value, min, max, step, setter, formatter));
    }

    @Override
    public void removed() {
        LazyChunksConfig.getInstance().save();
    }

    private void close() {
        LazyChunksConfig.getInstance().save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private enum Tab {
        GENERAL("lazy_chunks.tab.general"),
        DYNAMIC("lazy_chunks.tab.dynamic"),
        DEBUG("lazy_chunks.tab.debug");

        private final String titleKey;

        Tab(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private static final class ConfigSlider extends AbstractSliderButton {
        private final String labelKey;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer setter;
        private final DoubleFunction<Component> formatter;

        private ConfigSlider(int x, int y, int width, int height, String labelKey, double initialValue,
                             double min, double max, double step, DoubleConsumer setter, DoubleFunction<Component> formatter) {
            super(x, y, width, height, Component.empty(), normalize(initialValue, min, max));
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            this.formatter = formatter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(
                    "lazy_chunks.option.slider_message",
                    Component.translatable(this.labelKey),
                    this.formatter.apply(getSteppedValue())
            ));
        }

        @Override
        protected void applyValue() {
            this.setter.accept(getSteppedValue());
            updateMessage();
        }

        private double getSteppedValue() {
            double raw = this.min + (this.max - this.min) * this.value;
            double stepped = this.step <= 0 ? raw : Math.round(raw / this.step) * this.step;
            return clamp(stepped, this.min, this.max);
        }

        private static double normalize(double value, double min, double max) {
            if (max <= min) {
                return 0.0;
            }

            return (clamp(value, min, max) - min) / (max - min);
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
