package net.rizen.lazy_chunks.compat.sodium;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.client.gui.options.Option;
import net.caffeinemc.mods.sodium.client.gui.options.OptionGroup;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpl;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpact;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.network.chat.Component;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class LazyChunksLegacySodiumPage {
    private static final OptionStorage<LazyChunksConfig> STORAGE = new LazyChunksOptionStorage();

    private LazyChunksLegacySodiumPage() {
    }

    public static OptionPage create() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(group(
                booleanOption("\u542f\u7528", "\u542f\u7528LazyChunks\u533a\u5757\u52a0\u8f7d\u9650\u6d41\u3002",
                        (config, value) -> config.lazyChunkLoadingEnabled = value,
                        config -> config.lazyChunkLoadingEnabled),
                booleanOption("F3\u4fe1\u606f", "\u5728F3\u8c03\u8bd5\u754c\u9762\u663e\u793aLazyChunks\u4fe1\u606f\u3002",
                        (config, value) -> config.showDebugInfo = value,
                        config -> config.showDebugInfo),
                booleanOption("\u4e3b\u52a8\u8c03\u8282", "\u5728\u660e\u663e\u5361\u987f\u524d\u63d0\u524d\u51cf\u5c11\u533a\u5757\u5904\u7406\u91cf\u3002",
                        (config, value) -> config.proactiveThrottling = value,
                        config -> config.proactiveThrottling),
                booleanOption("\u4f20\u9001\u4fdd\u62a4", "\u4f20\u9001\u6216\u5207\u6362\u7ef4\u5ea6\u540e\u4f7f\u7528\u66f4\u4fdd\u5b88\u7684\u9650\u6d41\u3002",
                        (config, value) -> config.teleportProtection = value,
                        config -> config.teleportProtection),
                booleanOption("\u81ea\u9002\u5e94\u6838\u5fc3", "\u5f531%\u4f4e\u5e27FPS\u6301\u7eed\u504f\u4f4e\u4e14\u5e73\u5747FPS\u4ecd\u6709\u4f59\u91cf\u65f6\uff0c\u589e\u52a0\u989d\u5916\u533a\u5757\u9884\u7b97\u3002",
                        (config, value) -> config.adaptiveThrottleCoreBoost = value,
                        config -> config.adaptiveThrottleCoreBoost)
        ));

        groups.add(group(
                integerOption("\u76ee\u6807FPS", "\u7528\u4e8e\u8ba1\u7b97\u533a\u5757\u9884\u7b97\u7684\u57fa\u51c6FPS\u3002", 30, 240, 1,
                        ControlValueFormatter.number(),
                        (config, value) -> config.targetFps = value,
                        config -> config.targetFps),
                integerOption("FPS\u9608\u503c", "\u9ad8\u4e8e\u6b64FPS\u65f6\u53ef\u4f7f\u7528\u539f\u7248\u533a\u5757\u52a0\u8f7d\u884c\u4e3a\u3002", 30, 240, 1,
                        ControlValueFormatter.number(),
                        (config, value) -> config.fpsThreshold = value,
                        config -> config.fpsThreshold),
                decimalOption("\u57fa\u7840\u6743\u91cd", "\u8fbe\u5230\u76ee\u6807FPS\u65f6\u6bcf\u5e27\u5904\u7406\u7684\u533a\u5757\u5de5\u4f5c\u9884\u7b97\u3002", 5, 100, 1,
                        (config, value) -> config.baseWeightPerFrame = value / 10.0,
                        config -> toTenths(config.baseWeightPerFrame)),
                decimalOption("\u5e27\u65f6\u95f4%", "\u6bcf\u5e27\u7528\u4e8e\u533a\u5757\u5904\u7406\u7684\u6700\u5927\u5e27\u65f6\u95f4\u767e\u5206\u6bd4\u3002", 10, 200, 5,
                        (config, value) -> config.maxFrameTimePercent = value / 10.0,
                        config -> toTenths(config.maxFrameTimePercent)),
                decimalOption("\u6700\u5c0f\u9884\u7b97", "\u6700\u5c0f\u533a\u5757\u5de5\u4f5c\u9884\u7b97\uff0c\u5efa\u8bae\u4e0d\u4f4e\u4e8e2.6\u3002", 26, 100, 1,
                        (config, value) -> config.minimumBudget = value / 10.0,
                        config -> toTenths(config.minimumBudget))
        ));

        groups.add(group(
                integerOption("1%\u4f4e\u5e27FPS", "\u89e6\u53d1\u81ea\u9002\u5e94\u6838\u5fc3\u903b\u8f91\u76841%\u4f4e\u5e27FPS\u9608\u503c\u3002", 30, 240, 1,
                        ControlValueFormatter.number(),
                        (config, value) -> config.adaptiveLowOnePercentFpsThreshold = value,
                        config -> config.adaptiveLowOnePercentFpsThreshold),
                decimalOption("\u4f4e\u5e27\u6301\u7eed\u65f6\u95f4", "1%\u4f4e\u5e27FPS\u4f4e\u4e8e\u9608\u503c\u540e\uff0c\u5f00\u59cb\u81ea\u9002\u5e94\u6838\u5fc3\u68c0\u67e5\u6240\u9700\u7684\u6301\u7eed\u65f6\u95f4\u3002", 10, 120, 5,
                        (config, value) -> config.adaptiveLowOnePercentDurationSeconds = value / 10.0,
                        config -> toTenths(config.adaptiveLowOnePercentDurationSeconds)),
                integerOption("\u5e73\u5747FPS", "\u5141\u8bb8\u589e\u52a0\u81ea\u9002\u5e94\u6838\u5fc3\u9884\u7b97\u6240\u9700\u7684\u5e73\u5747FPS\u3002", 30, 300, 1,
                        ControlValueFormatter.number(),
                        (config, value) -> config.adaptiveAverageFpsThreshold = value,
                        config -> config.adaptiveAverageFpsThreshold),
                integerOption("\u6700\u5927\u6838\u5fc3", "\u6700\u591a\u6dfb\u52a0\u5230\u533a\u5757\u9884\u7b97\u7684\u989d\u5916\u81ea\u9002\u5e94\u6838\u5fc3\u6570\u3002", 1, 3, 1,
                        ControlValueFormatter.number(),
                        (config, value) -> config.adaptiveMaxExtraCores = value,
                        config -> config.adaptiveMaxExtraCores),
                integerOption("\u6bcf\u6838\u5fc3FPS", "\u6bcf\u589e\u52a0\u4e00\u4e2a\u989d\u5916\u81ea\u9002\u5e94\u6838\u5fc3\u6240\u9700\u7684\u5e73\u5747FPS\u589e\u91cf\u3002", 10, 120, 1,
                        ControlValueFormatter.number(),
                        (config, value) -> config.adaptiveFpsPerExtraCore = value,
                        config -> config.adaptiveFpsPerExtraCore),
                decimalOption("\u6838\u5fc3\u6743\u91cd", "\u6bcf\u4e2a\u81ea\u9002\u5e94\u6838\u5fc3\u6dfb\u52a0\u7684\u533a\u5757\u5de5\u4f5c\u9884\u7b97\u3002", 1, 50, 1,
                        (config, value) -> config.adaptiveCoreWeight = value / 10.0,
                        config -> toTenths(config.adaptiveCoreWeight))
        ));

        return new OptionPage(Component.literal("LazyChunks\u8bbe\u7f6e"), ImmutableList.copyOf(groups));
    }

    @SafeVarargs
    private static OptionGroup group(Option<?>... options) {
        OptionGroup.Builder builder = OptionGroup.createBuilder();
        for (Option<?> option : options) {
            builder.add(option);
        }
        return builder.build();
    }

    private static Option<Boolean> booleanOption(
            String name,
            String tooltip,
            BiConsumer<LazyChunksConfig, Boolean> setter,
            Function<LazyChunksConfig, Boolean> getter
    ) {
        return OptionImpl.createBuilder(Boolean.TYPE, STORAGE)
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setControl(TickBoxControl::new)
                .setBinding(setter, getter)
                .setImpact(OptionImpact.LOW)
                .build();
    }

    private static Option<Integer> integerOption(
            String name,
            String tooltip,
            int min,
            int max,
            int step,
            ControlValueFormatter formatter,
            BiConsumer<LazyChunksConfig, Integer> setter,
            Function<LazyChunksConfig, Integer> getter
    ) {
        return OptionImpl.createBuilder(Integer.TYPE, STORAGE)
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setControl(option -> new SliderControl(option, min, max, step, formatter))
                .setBinding(setter, getter)
                .setImpact(OptionImpact.MEDIUM)
                .build();
    }

    private static Option<Integer> decimalOption(
            String name,
            String tooltip,
            int min,
            int max,
            int step,
            BiConsumer<LazyChunksConfig, Integer> setter,
            Function<LazyChunksConfig, Integer> getter
    ) {
        return integerOption(name, tooltip, min, max, step, LazyChunksLegacySodiumPage::formatTenths, setter, getter);
    }

    private static Component formatTenths(int value) {
        return Component.literal(String.format(Locale.ROOT, "%.1f", value / 10.0));
    }

    private static int toTenths(double value) {
        return (int) Math.round(value * 10.0);
    }

    private static final class LazyChunksOptionStorage implements OptionStorage<LazyChunksConfig> {
        @Override
        public LazyChunksConfig getData() {
            return LazyChunksConfig.getInstance();
        }

        @Override
        public void save() {
            LazyChunksConfig.getInstance().save();
        }
    }
}
