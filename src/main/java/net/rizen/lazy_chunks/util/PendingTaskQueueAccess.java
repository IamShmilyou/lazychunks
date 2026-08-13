package net.rizen.lazy_chunks.util;

import net.minecraft.util.thread.BlockableEventLoop;
import net.rizen.lazy_chunks.LazyChunksMod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

public final class PendingTaskQueueAccess {
    private static Field pendingRunnablesField;
    private static boolean lookupAttempted;
    private static boolean warningLogged;

    private PendingTaskQueueAccess() {
    }

    @SuppressWarnings("unchecked")
    public static Queue<Runnable> getPendingRunnables(BlockableEventLoop<?> eventLoop) {
        Field field = getPendingRunnablesField(eventLoop.getClass());
        if (field == null) {
            logWarningOnce("Unable to find Minecraft pending task queue; LazyChunks will use vanilla task processing.");
            return null;
        }

        try {
            Object value = field.get(eventLoop);
            if (value instanceof Queue<?>) {
                return (Queue<Runnable>) value;
            }

            logWarningOnce("Minecraft pending task queue field had unexpected type; LazyChunks will use vanilla task processing.");
        } catch (IllegalAccessException | RuntimeException e) {
            logWarningOnce("Unable to read Minecraft pending task queue; LazyChunks will use vanilla task processing.", e);
        }

        return null;
    }

    private static Field getPendingRunnablesField(Class<?> runtimeClass) {
        if (pendingRunnablesField != null) {
            return pendingRunnablesField;
        }

        if (lookupAttempted) {
            return null;
        }

        lookupAttempted = true;

        Class<?> targetClass = getBlockableEventLoopClass(runtimeClass);

        Set<String> names = new LinkedHashSet<>();
        names.add("pendingRunnables");
        names.add("f_18682_");
        names.add("field_5750");

        for (Class<?> type = targetClass; type != null; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    if (Queue.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        pendingRunnablesField = field;
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Try the next known runtime name.
                } catch (RuntimeException e) {
                    logWarningOnce("Unable to prepare Minecraft pending task queue access; LazyChunks will use vanilla task processing.", e);
                    return null;
                }
            }
        }

        for (Class<?> type = targetClass; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && Queue.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        pendingRunnablesField = field;
                        return field;
                    } catch (RuntimeException e) {
                        logWarningOnce("Unable to prepare Minecraft pending task queue access; LazyChunks will use vanilla task processing.", e);
                        return null;
                    }
                }
            }
        }

        return null;
    }

    private static Class<?> getBlockableEventLoopClass(Class<?> runtimeClass) {
        for (Class<?> type = runtimeClass; type != null; type = type.getSuperclass()) {
            if (type == BlockableEventLoop.class) {
                return type;
            }
        }

        return BlockableEventLoop.class;
    }

    private static void logWarningOnce(String message) {
        if (!warningLogged) {
            warningLogged = true;
            LazyChunksMod.LOGGER.warn(message);
        }
    }

    private static void logWarningOnce(String message, Throwable throwable) {
        if (!warningLogged) {
            warningLogged = true;
            LazyChunksMod.LOGGER.warn(message, throwable);
        }
    }
}
