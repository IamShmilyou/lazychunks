package net.rizen.lazy_chunks.sodium;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.PageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.config.LazyChunksConfig;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class LazyChunksSodiumConfigEntrypoint implements ConfigEntryPoint {
    private static final StorageEventHandler STORAGE_HANDLER = () -> LazyChunksConfig.getInstance().save();
    private static final LazyChunksConfig DEFAULTS = new LazyChunksConfig();

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        PageBuilder configuredPage;
        try {
            Object page = invoke(builder, "createOptionPage");
            setName(page, "lazy_chunks.options.title");
            addOptions(builder, page);
            if (!(page instanceof PageBuilder pageBuilder)) {
                throw new IllegalStateException("Sodium createOptionPage did not return a PageBuilder");
            }
            configuredPage = pageBuilder;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LazyChunksMod.LOGGER.warn("Failed to register inline LazyChunks Sodium config page", e);
            return;
        }

        ModOptionsBuilder options = builder.registerOwnModOptions();
        try {
            invoke(options, "setNonTintedIcon", id("textures/gui/sodium_icon.png"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LazyChunksMod.LOGGER.warn("Failed to set LazyChunks Sodium config icon", e);
        }
        options.addPage(configuredPage);
        options.setName("LazyChunks");
        options.setVersion(LazyChunksMod.VERSION);
        LazyChunksMod.LOGGER.info("Registered inline LazyChunks Sodium config page");
    }

    private static void addOptions(ConfigBuilder builder, Object page) throws ReflectiveOperationException {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        OptionGroupBuilder general = builder.createOptionGroup();
        setName(general, "lazy_chunks.options.header.general");
        general.addOption(booleanOption(builder, "lazy_chunk_loading_enabled", "lazyChunkLoadingEnabled",
                DEFAULTS.lazyChunkLoadingEnabled,
                value -> config.lazyChunkLoadingEnabled = value,
                () -> config.lazyChunkLoadingEnabled));
        general.addOption(booleanOption(builder, "show_debug_overlay_info", "showDebugOverlayInfo",
                DEFAULTS.showDebugOverlayInfo,
                value -> config.showDebugOverlayInfo = value,
                () -> config.showDebugOverlayInfo));
        general.addOption(integerOption(builder, "target_fps", "targetFps", 1, 1000,
                DEFAULTS.targetFps,
                value -> config.targetFps = value,
                () -> config.targetFps));
        general.addOption(integerOption(builder, "fps_threshold", "fpsThreshold", 1, 1000,
                DEFAULTS.fpsThreshold,
                value -> config.fpsThreshold = value,
                () -> config.fpsThreshold));
        general.addOption(decimalOption(builder, "base_weight_per_frame", "baseWeightPerFrame", 1, 10000,
                DEFAULTS.baseWeightPerFrame,
                value -> config.baseWeightPerFrame = value,
                () -> config.baseWeightPerFrame));
        general.addOption(decimalOption(builder, "max_frame_time_percent", "maxFrameTimePercent", 10, 200,
                DEFAULTS.maxFrameTimePercent,
                value -> config.maxFrameTimePercent = value,
                () -> config.maxFrameTimePercent));
        general.addOption(decimalOption(builder, "minimum_budget", "minimumBudget", 26, 10000,
                DEFAULTS.minimumBudget,
                value -> config.minimumBudget = value,
                () -> config.minimumBudget));
        general.addOption(booleanOption(builder, "proactive_throttling", "proactiveThrottling",
                DEFAULTS.proactiveThrottling,
                value -> config.proactiveThrottling = value,
                () -> config.proactiveThrottling));
        general.addOption(booleanOption(builder, "teleport_protection", "teleportProtection",
                DEFAULTS.teleportProtection,
                value -> config.teleportProtection = value,
                () -> config.teleportProtection));

        OptionGroupBuilder adaptive = builder.createOptionGroup();
        setName(adaptive, "lazy_chunks.options.header.adaptive");
        adaptive.addOption(booleanOption(builder, "adaptive_budget_boost_enabled", "adaptiveBudgetBoostEnabled",
                DEFAULTS.adaptiveBudgetBoostEnabled,
                value -> config.adaptiveBudgetBoostEnabled = value,
                () -> config.adaptiveBudgetBoostEnabled));
        adaptive.addOption(integerOption(builder, "adaptive_low_fps_threshold", "adaptiveLowFpsThreshold", 1, 1000,
                DEFAULTS.adaptiveLowFpsThreshold,
                value -> config.adaptiveLowFpsThreshold = value,
                () -> config.adaptiveLowFpsThreshold));
        adaptive.addOption(decimalOption(builder, "adaptive_low_fps_duration_seconds", "adaptiveLowFpsDurationSeconds", 100, 150,
                DEFAULTS.adaptiveLowFpsDurationSeconds,
                value -> config.adaptiveLowFpsDurationSeconds = value,
                () -> config.adaptiveLowFpsDurationSeconds));
        adaptive.addOption(integerOption(builder, "adaptive_average_fps_threshold", "adaptiveAverageFpsThreshold", 1, 1000,
                DEFAULTS.adaptiveAverageFpsThreshold,
                value -> config.adaptiveAverageFpsThreshold = value,
                () -> config.adaptiveAverageFpsThreshold));
        adaptive.addOption(integerOption(builder, "adaptive_max_boost_cores", "adaptiveMaxBoostCores", 1, 3,
                DEFAULTS.adaptiveMaxBoostCores,
                value -> config.adaptiveMaxBoostCores = value,
                () -> config.adaptiveMaxBoostCores));
        adaptive.addOption(decimalOption(builder, "adaptive_boost_weight_per_core", "adaptiveBoostWeightPerCore", 1, 100,
                DEFAULTS.adaptiveBoostWeightPerCore,
                value -> config.adaptiveBoostWeightPerCore = value,
                () -> config.adaptiveBoostWeightPerCore));
        adaptive.addOption(integerOption(builder, "adaptive_fps_per_boost_core", "adaptiveFpsPerBoostCore", 1, 1000,
                DEFAULTS.adaptiveFpsPerBoostCore,
                value -> config.adaptiveFpsPerBoostCore = value,
                () -> config.adaptiveFpsPerBoostCore));

        invoke(page, "addOptionGroup", general);
        invoke(page, "addOptionGroup", adaptive);
    }

    private static OptionBuilder booleanOption(ConfigBuilder builder, String idPath, String key, boolean defaultValue,
                                               Consumer<Boolean> setter, Supplier<Boolean> getter) throws ReflectiveOperationException {
        Object option = invoke(builder, "createBooleanOption", id(idPath));
        if (!(option instanceof BooleanOptionBuilder booleanOption)) {
            throw new IllegalStateException("Sodium createBooleanOption did not return a BooleanOptionBuilder");
        }

        setName(option, optionKey(key));
        setTooltip(option, key);
        return booleanOption
                .setStorageHandler(STORAGE_HANDLER)
                .setDefaultValue(defaultValue)
                .setBinding(setter, getter);
    }

    private static OptionBuilder integerOption(ConfigBuilder builder, String idPath, String key, int min, int max,
                                               int defaultValue, Consumer<Integer> setter,
                                               Supplier<Integer> getter) throws ReflectiveOperationException {
        Object option = invoke(builder, "createIntegerOption", id(idPath));
        if (!(option instanceof IntegerOptionBuilder integerOption)) {
            throw new IllegalStateException("Sodium createIntegerOption did not return an IntegerOptionBuilder");
        }

        setName(option, optionKey(key));
        setTooltip(option, key);
        return integerOption
                .setStorageHandler(STORAGE_HANDLER)
                .setDefaultValue(clamp(defaultValue, min, max))
                .setBinding(setter, () -> clamp(getter.get(), min, max))
                .setRange(min, max, 1)
                .setValueFormatter(formatter(value -> Component.literal(Integer.toString(value))));
    }

    private static OptionBuilder decimalOption(ConfigBuilder builder, String idPath, String key, int minTenths, int maxTenths,
                                               double defaultValue, Consumer<Double> setter,
                                               Supplier<Double> getter) throws ReflectiveOperationException {
        Object option = invoke(builder, "createIntegerOption", id(idPath));
        if (!(option instanceof IntegerOptionBuilder integerOption)) {
            throw new IllegalStateException("Sodium createIntegerOption did not return an IntegerOptionBuilder");
        }

        setName(option, optionKey(key));
        setTooltip(option, key);
        return integerOption
                .setStorageHandler(STORAGE_HANDLER)
                .setDefaultValue(clamp(toTenths(defaultValue), minTenths, maxTenths))
                .setBinding(value -> setter.accept(value / 10.0D), () -> clamp(toTenths(getter.get()), minTenths, maxTenths))
                .setRange(minTenths, maxTenths, 1)
                .setValueFormatter(formatter(value -> Component.literal(String.format(Locale.ROOT, "%.1f", value / 10.0D))));
    }

    private static void setName(Object target, String translationKey) throws ReflectiveOperationException {
        invoke(target, "setName", Component.translatable(translationKey));
    }

    private static void setTooltip(Object target, String key) throws ReflectiveOperationException {
        invoke(target, "setTooltip", Component.translatable(optionKey(key) + ".tooltip"));
    }

    private static String optionKey(String key) {
        return "lazy_chunks.option." + key;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LazyChunksMod.MOD_ID, path);
    }

    private static ControlValueFormatter formatter(IntFunction<Component> formatter) {
        return (ControlValueFormatter) Proxy.newProxyInstance(
                ControlValueFormatter.class.getClassLoader(),
                new Class<?>[]{ControlValueFormatter.class},
                (proxy, method, args) -> {
                    if ("format".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Integer value) {
                        return formatter.apply(value);
                    }
                    if ("toString".equals(method.getName()) && (args == null || args.length == 0)) {
                        return "LazyChunksControlValueFormatter";
                    }
                    if ("hashCode".equals(method.getName()) && (args == null || args.length == 0)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findCompatibleMethod(target, methodName, args);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Method findCompatibleMethod(Object target, String methodName, Object[] args) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            if (parametersMatch(method.getParameterTypes(), args)) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (parameterTypes[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!wrap(parameterTypes[i]).isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    private static int toTenths(double value) {
        return (int) Math.round(value * 10.0D);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
