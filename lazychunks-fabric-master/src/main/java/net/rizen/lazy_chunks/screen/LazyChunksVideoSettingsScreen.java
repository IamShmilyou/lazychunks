package net.rizen.lazy_chunks.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class LazyChunksVideoSettingsScreen extends VideoSettingsScreen {
    private static final Component VIDEO_TAB_TITLE = Component.translatable("options.video");
    private static final Component LAZY_CHUNKS_TAB_TITLE = Component.literal("LazyChunks\u8bbe\u7f6e");
    private static final int SMALL_WIDGET_WIDTH = 150;
    private static final int SMALL_WIDGET_HEIGHT = 20;

    private TabManager tabManager;
    private TabNavigationBar tabNavigationBar;
    private OptionsList videoOptionsList;
    private OptionsList lazyChunksOptionsList;
    private Button doneButton;

    public LazyChunksVideoSettingsScreen(Screen parent, Minecraft minecraft, Options options) {
        super(parent, minecraft, options);
    }

    @Override
    protected void init() {
        this.tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
        this.videoOptionsList = this.createVideoOptionsList();
        this.lazyChunksOptionsList = this.createLazyChunksOptionsList();

        Tab videoTab = new OptionsListTab(VIDEO_TAB_TITLE, this.videoOptionsList);
        Tab lazyChunksTab = new OptionsListTab(LAZY_CHUNKS_TAB_TITLE, this.lazyChunksOptionsList);

        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(videoTab, lazyChunksTab)
                .build();
        this.addRenderableWidget(this.tabNavigationBar);

        this.doneButton = Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(200)
                .build();
        this.addRenderableWidget(this.doneButton);

        this.tabNavigationBar.selectTab(0, false);
        this.repositionElements();
    }

    private OptionsList createVideoOptionsList() {
        OptionsList optionsList = new OptionsList(this.minecraft, this.width, this);
        this.list = optionsList;
        super.addOptions();
        return optionsList;
    }

    private OptionsList createLazyChunksOptionsList() {
        OptionsList optionsList = new OptionsList(this.minecraft, this.width, this);
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        optionsList.addSmall(List.of(
                booleanButton("\u542f\u7528", config.lazyChunkLoadingEnabled, value -> config.lazyChunkLoadingEnabled = value),
                booleanButton("F3\u4fe1\u606f", config.showDebugInfo, value -> config.showDebugInfo = value),
                booleanButton("\u4e3b\u52a8\u8c03\u8282", config.proactiveThrottling, value -> config.proactiveThrottling = value),
                booleanButton("\u4f20\u9001\u4fdd\u62a4", config.teleportProtection, value -> config.teleportProtection = value),
                booleanButton("\u81ea\u9002\u5e94\u6838\u5fc3", config.adaptiveThrottleCoreBoost, value -> config.adaptiveThrottleCoreBoost = value),
                intSlider("\u76ee\u6807FPS", 30, 240, config.targetFps, () -> config.targetFps, value -> config.targetFps = (int) value),
                intSlider("FPS\u9608\u503c", 30, 240, config.fpsThreshold, () -> config.fpsThreshold, value -> config.fpsThreshold = (int) value),
                doubleSlider("\u57fa\u7840\u6743\u91cd", 0.5, 10.0, 0.1, config.baseWeightPerFrame, () -> config.baseWeightPerFrame, value -> config.baseWeightPerFrame = value),
                doubleSlider("\u5e27\u65f6\u95f4%", 1.0, 20.0, 0.5, config.maxFrameTimePercent, () -> config.maxFrameTimePercent, value -> config.maxFrameTimePercent = value),
                doubleSlider("\u6700\u5c0f\u9884\u7b97", 2.6, 10.0, 0.1, config.minimumBudget, () -> config.minimumBudget, value -> config.minimumBudget = value),
                intSlider("1%\u4f4e\u5e27FPS", 30, 240, config.adaptiveLowOnePercentFpsThreshold, () -> config.adaptiveLowOnePercentFpsThreshold, value -> config.adaptiveLowOnePercentFpsThreshold = (int) value),
                doubleSlider("\u4f4e\u5e27\u6301\u7eed\u65f6\u95f4", 1.0, 12.0, 0.5, config.adaptiveLowOnePercentDurationSeconds, () -> config.adaptiveLowOnePercentDurationSeconds, value -> config.adaptiveLowOnePercentDurationSeconds = value),
                intSlider("\u5e73\u5747FPS", 30, 300, config.adaptiveAverageFpsThreshold, () -> config.adaptiveAverageFpsThreshold, value -> config.adaptiveAverageFpsThreshold = (int) value),
                intSlider("\u6700\u5927\u6838\u5fc3", 1, 3, config.adaptiveMaxExtraCores, () -> config.adaptiveMaxExtraCores, value -> config.adaptiveMaxExtraCores = (int) value),
                intSlider("\u6bcf\u6838\u5fc3FPS", 10, 120, config.adaptiveFpsPerExtraCore, () -> config.adaptiveFpsPerExtraCore, value -> config.adaptiveFpsPerExtraCore = (int) value),
                doubleSlider("\u6838\u5fc3\u6743\u91cd", 0.1, 5.0, 0.1, config.adaptiveCoreWeight, () -> config.adaptiveCoreWeight, value -> config.adaptiveCoreWeight = value)
        ));

        optionsList.addSmall(Button.builder(Component.literal("\u91cd\u7f6eLazyChunks"), button -> {
                    resetConfig(config);
                    config.save();
                    this.minecraft.setScreen(new LazyChunksVideoSettingsScreen(this.lastScreen, this.minecraft, this.options));
                })
                .width(310)
                .tooltip(Tooltip.create(Component.literal("\u6062\u590dLazyChunks\u9ed8\u8ba4\u8bbe\u7f6e\u3002")))
                .build(), null);

        return optionsList;
    }

    private static CycleButton<Boolean> booleanButton(String label, boolean initialValue, BooleanSetter setter) {
        LazyChunksConfig config = LazyChunksConfig.getInstance();
        return CycleButton.onOffBuilder(initialValue)
                .create(0, 0, SMALL_WIDGET_WIDTH, SMALL_WIDGET_HEIGHT, Component.literal(label), (button, value) -> {
                    setter.set(value);
                    config.save();
                });
    }

    private static ConfigSlider intSlider(String label, int min, int max, int initialValue, DoubleSupplier getter, DoubleConsumer setter) {
        return new ConfigSlider(label, min, max, 1.0, initialValue, getter, setter, true);
    }

    private static ConfigSlider doubleSlider(String label, double min, double max, double step, double initialValue, DoubleSupplier getter, DoubleConsumer setter) {
        return new ConfigSlider(label, min, max, step, initialValue, getter, setter, false);
    }

    private static void resetConfig(LazyChunksConfig config) {
        LazyChunksConfig defaults = new LazyChunksConfig();

        config.lazyChunkLoadingEnabled = defaults.lazyChunkLoadingEnabled;
        config.showDebugInfo = defaults.showDebugInfo;
        config.targetFps = defaults.targetFps;
        config.fpsThreshold = defaults.fpsThreshold;
        config.baseWeightPerFrame = defaults.baseWeightPerFrame;
        config.maxFrameTimePercent = defaults.maxFrameTimePercent;
        config.proactiveThrottling = defaults.proactiveThrottling;
        config.teleportProtection = defaults.teleportProtection;
        config.minimumBudget = defaults.minimumBudget;
        config.adaptiveThrottleCoreBoost = defaults.adaptiveThrottleCoreBoost;
        config.adaptiveLowOnePercentFpsThreshold = defaults.adaptiveLowOnePercentFpsThreshold;
        config.adaptiveLowOnePercentDurationSeconds = defaults.adaptiveLowOnePercentDurationSeconds;
        config.adaptiveAverageFpsThreshold = defaults.adaptiveAverageFpsThreshold;
        config.adaptiveMaxExtraCores = defaults.adaptiveMaxExtraCores;
        config.adaptiveFpsPerExtraCore = defaults.adaptiveFpsPerExtraCore;
        config.adaptiveCoreWeight = defaults.adaptiveCoreWeight;
    }

    @Override
    protected void repositionElements() {
        if (this.tabNavigationBar == null || this.tabManager == null) {
            return;
        }

        this.tabNavigationBar.setWidth(this.width);
        this.tabNavigationBar.arrangeElements();

        int tabBottom = this.tabNavigationBar.getRectangle().bottom();
        int contentHeight = Math.max(0, this.height - this.layout.getFooterHeight() - tabBottom);
        this.tabManager.setTabArea(new ScreenRectangle(0, tabBottom, this.width, contentHeight));

        if (this.doneButton != null) {
            this.doneButton.setPosition(this.width / 2 - 100, this.height - 27);
        }

        this.layout.setHeaderHeight(tabBottom);
        this.layout.arrangeElements();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.tabNavigationBar != null && this.tabNavigationBar.keyPressed(keyCode)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.list == this.lazyChunksOptionsList && Screen.hasControlDown()) {
            return this.lazyChunksOptionsList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.videoOptionsList != null) {
            this.videoOptionsList.applyUnsavedChanges();
        }

        LazyChunksConfig.getInstance().save();
        super.onClose();
    }

    @Override
    public void removed() {
        LazyChunksConfig.getInstance().save();
        super.removed();
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }

    private class OptionsListTab implements Tab {
        private final Component title;
        private final OptionsList optionsList;

        private OptionsListTab(Component title, OptionsList optionsList) {
            this.title = title;
            this.optionsList = optionsList;
        }

        @Override
        public Component getTabTitle() {
            return this.title;
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> consumer) {
            consumer.accept(this.optionsList);
        }

        @Override
        public void doLayout(ScreenRectangle screenRectangle) {
            LazyChunksVideoSettingsScreen.this.list = this.optionsList;
            this.optionsList.updateSizeAndPosition(screenRectangle.width(), screenRectangle.height(), screenRectangle.top());
        }
    }

    private static class ConfigSlider extends AbstractSliderButton {
        private final Component label;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final boolean integerValue;

        private ConfigSlider(String label, double min, double max, double step, double initialValue, DoubleSupplier getter, DoubleConsumer setter, boolean integerValue) {
            super(0, 0, SMALL_WIDGET_WIDTH, SMALL_WIDGET_HEIGHT, Component.empty(), normalize(initialValue, min, max));
            this.label = Component.literal(label);
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.integerValue = integerValue;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(CommonComponents.optionNameValue(this.label, Component.literal(this.formatValue(this.denormalize(this.value)))));
        }

        @Override
        protected void applyValue() {
            double actualValue = this.roundToStep(this.denormalize(this.value));
            this.value = normalize(actualValue, this.min, this.max);
            this.setter.accept(actualValue);
            LazyChunksConfig.getInstance().save();
            this.updateMessage();
        }

        private double denormalize(double sliderValue) {
            return this.min + (this.max - this.min) * sliderValue;
        }

        private double roundToStep(double actualValue) {
            double roundedValue = Math.round(actualValue / this.step) * this.step;
            return Math.max(this.min, Math.min(this.max, roundedValue));
        }

        private String formatValue(double actualValue) {
            double currentValue = this.roundToStep(this.getter.getAsDouble());
            if (Math.abs(currentValue - actualValue) > this.step * 0.5) {
                currentValue = this.roundToStep(actualValue);
            }

            if (this.integerValue) {
                return Integer.toString((int) Math.round(currentValue));
            }

            return String.format("%.1f", currentValue);
        }

        private static double normalize(double actualValue, double min, double max) {
            if (max <= min) {
                return 0.0;
            }

            return Math.max(0.0, Math.min(1.0, (actualValue - min) / (max - min)));
        }
    }
}
