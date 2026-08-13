package net.rizen.lazy_chunks.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.util.thread.ThreadExecutor;
import net.rizen.lazy_chunks.LazyChunksMod;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

public final class PendingTaskQueueAccess {
    private static final String OWNER_NAMED = "net.minecraft.util.thread.ThreadExecutor";
    private static final String FIELD_NAMED = "tasks";
    private static final String FIELD_DESCRIPTOR = "Ljava/util/Queue;";

    private static Field pendingRunnablesField;
    private static boolean lookupAttempted;
    private static boolean warningLogged;

    private PendingTaskQueueAccess() {
    }

    @SuppressWarnings("unchecked")
    public static Queue<Runnable> getPendingRunnables(ThreadExecutor<?> eventLoop) {
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
        Set<String> candidateNames = getCandidateFieldNames();

        for (Class<?> currentClass = runtimeClass; currentClass != null; currentClass = currentClass.getSuperclass()) {
            for (String candidateName : candidateNames) {
                try {
                    Field field = currentClass.getDeclaredField(candidateName);
                    if (Queue.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        pendingRunnablesField = field;
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Try the next mapped name or superclass.
                } catch (RuntimeException e) {
                    logWarningOnce("Unable to prepare Minecraft pending task queue access; LazyChunks will use vanilla task processing.", e);
                    return null;
                }
            }
        }

        return null;
    }

    private static Set<String> getCandidateFieldNames() {
        Set<String> candidateNames = new LinkedHashSet<>();
        candidateNames.add(FIELD_NAMED);

        try {
            MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
            candidateNames.add(mappings.mapFieldName("named", OWNER_NAMED, FIELD_NAMED, FIELD_DESCRIPTOR));
        } catch (RuntimeException ignored) {
            // Development environments often expose named fields directly.
        }

        return candidateNames;
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
