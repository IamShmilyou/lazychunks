package net.rizen.lazy_chunks.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.Locale;
import java.util.function.Consumer;

public class LazyChunksConfigScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("lazy_chunks.options.title");
    private boolean skipApplyOnRemoved = false;

    public LazyChunksConfigScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
    }

    public static void open(Screen lastScreen) {
        Minecraft.getInstance().setScreen(new LazyChunksConfigScreen(lastScreen));
    }

    @Override
    protected void addOptions() {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        this.list.addHeader(Component.translatable(FabricLoader.getInstance().isModLoaded("sodium")
                ? "lazy_chunks.options.header.sodium"
                : "lazy_chunks.options.header.vanilla"));
        this.list.addSmall(
                booleanOption("lazyChunkLoadingEnabled", config.lazyChunkLoadingEnabled, value -> config.lazyChunkLoadingEnabled = value),
                booleanOption("showDebugOverlayInfo", config.showDebugOverlayInfo, value -> config.showDebugOverlayInfo = value)
        );
        this.list.addSmall(
                intOption("targetFps", 1, 1000, config.targetFps, value -> config.targetFps = value),
                intOption("fpsThreshold", 1, 1000, config.fpsThreshold, value -> config.fpsThreshold = value)
        );
        this.list.addSmall(
                decimalOption("baseWeightPerFrame", 1, 10000, config.baseWeightPerFrame, value -> config.baseWeightPerFrame = value),
                decimalOption("maxFrameTimePercent", 10, 200, config.maxFrameTimePercent, value -> config.maxFrameTimePercent = value)
        );
        this.list.addSmall(
                decimalOption("minimumBudget", 26, 10000, config.minimumBudget, value -> config.minimumBudget = value),
                booleanOption("proactiveThrottling", config.proactiveThrottling, value -> config.proactiveThrottling = value)
        );
        this.list.addSmall(
                booleanOption("teleportProtection", config.teleportProtection, value -> config.teleportProtection = value)
        );

        this.list.addHeader(Component.translatable("lazy_chunks.options.header.adaptive"));
        this.list.addSmall(
                booleanOption("adaptiveBudgetBoostEnabled", config.adaptiveBudgetBoostEnabled, value -> config.adaptiveBudgetBoostEnabled = value),
                intOption("adaptiveLowFpsThreshold", 1, 1000, config.adaptiveLowFpsThreshold, value -> config.adaptiveLowFpsThreshold = value)
        );
        this.list.addSmall(
                decimalOption("adaptiveLowFpsDurationSeconds", 100, 150, config.adaptiveLowFpsDurationSeconds, value -> config.adaptiveLowFpsDurationSeconds = value),
                intOption("adaptiveAverageFpsThreshold", 1, 1000, config.adaptiveAverageFpsThreshold, value -> config.adaptiveAverageFpsThreshold = value)
        );
        this.list.addSmall(
                intOption("adaptiveMaxBoostCores", 1, 3, config.adaptiveMaxBoostCores, value -> config.adaptiveMaxBoostCores = value),
                decimalOption("adaptiveBoostWeightPerCore", 1, 100, config.adaptiveBoostWeightPerCore, value -> config.adaptiveBoostWeightPerCore = value)
        );
        this.list.addSmall(intOption("adaptiveFpsPerBoostCore", 1, 1000, config.adaptiveFpsPerBoostCore, value -> config.adaptiveFpsPerBoostCore = value));
        this.list.addSmall(resetButton(), null);
    }

    private static OptionInstance<Boolean> booleanOption(String key, boolean initialValue, Consumer<Boolean> setter) {
        return OptionInstance.createBoolean(
                optionKey(key),
                tooltip(key),
                initialValue,
                setter
        );
    }

    private static OptionInstance<Integer> intOption(String key, int min, int max, int initialValue, Consumer<Integer> setter) {
        return new OptionInstance<>(
                optionKey(key),
                tooltip(key),
                LazyChunksConfigScreen::intLabel,
                new OptionInstance.IntRange(min, max),
                clamp(initialValue, min, max),
                setter
        );
    }

    private static OptionInstance<Integer> decimalOption(String key, int minTenths, int maxTenths, double initialValue, Consumer<Double> setter) {
        return new OptionInstance<>(
                optionKey(key),
                tooltip(key),
                LazyChunksConfigScreen::decimalLabel,
                new OptionInstance.IntRange(minTenths, maxTenths),
                clamp((int) Math.round(initialValue * 10.0D), minTenths, maxTenths),
                value -> setter.accept(value / 10.0D)
        );
    }

    private Button resetButton() {
        return Button.builder(Component.translatable("lazy_chunks.options.reset"), button -> {
            LazyChunksConfig defaults = new LazyChunksConfig();
            LazyChunksConfig config = LazyChunksConfig.getInstance();

            config.lazyChunkLoadingEnabled = defaults.lazyChunkLoadingEnabled;
            config.showDebugOverlayInfo = defaults.showDebugOverlayInfo;
            config.targetFps = defaults.targetFps;
            config.fpsThreshold = defaults.fpsThreshold;
            config.baseWeightPerFrame = defaults.baseWeightPerFrame;
            config.maxFrameTimePercent = defaults.maxFrameTimePercent;
            config.proactiveThrottling = defaults.proactiveThrottling;
            config.teleportProtection = defaults.teleportProtection;
            config.minimumBudget = defaults.minimumBudget;
            config.adaptiveBudgetBoostEnabled = defaults.adaptiveBudgetBoostEnabled;
            config.adaptiveLowFpsThreshold = defaults.adaptiveLowFpsThreshold;
            config.adaptiveLowFpsDurationSeconds = defaults.adaptiveLowFpsDurationSeconds;
            config.adaptiveAverageFpsThreshold = defaults.adaptiveAverageFpsThreshold;
            config.adaptiveMaxBoostCores = defaults.adaptiveMaxBoostCores;
            config.adaptiveBoostWeightPerCore = defaults.adaptiveBoostWeightPerCore;
            config.adaptiveFpsPerBoostCore = defaults.adaptiveFpsPerBoostCore;
            config.save();

            this.skipApplyOnRemoved = true;
            Minecraft.getInstance().setScreen(new LazyChunksConfigScreen(this.lastScreen));
        }).width(310).build();
    }

    @Override
    public void onClose() {
        applyAndSave();
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public void removed() {
        if (!this.skipApplyOnRemoved) {
            applyAndSave();
        }
        super.removed();
    }

    private void applyAndSave() {
        OptionsList optionsList = this.list;
        if (optionsList != null) {
            optionsList.applyUnsavedChanges();
        }

        LazyChunksConfig.getInstance().save();
    }

    private static String optionKey(String key) {
        return "lazy_chunks.option." + key;
    }

    private static <T> OptionInstance.TooltipSupplier<T> tooltip(String key) {
        return OptionInstance.cachedConstantTooltip(Component.translatable("lazy_chunks.option." + key + ".tooltip"));
    }

    private static Component intLabel(Component caption, Integer value) {
        return Options.genericValueLabel(caption, Component.literal(Integer.toString(value)));
    }

    private static Component decimalLabel(Component caption, Integer value) {
        return Options.genericValueLabel(caption, Component.literal(String.format(Locale.ROOT, "%.1f", value / 10.0D)));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
