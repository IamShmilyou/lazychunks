package net.rizen.lazy_chunks;

import net.neoforged.fml.ModList;
import net.rizen.lazy_chunks.compat.sodium.LazyChunksSodiumOptionsApiCompat;

public final class LazyChunksClientMod {
    private static boolean initialized;

    private LazyChunksClientMod() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        registerSodiumOptionsApiCompat();
    }

    private static void registerSodiumOptionsApiCompat() {
        if (!ModList.get().isLoaded("sodiumoptionsapi")) {
            return;
        }

        try {
            if (LazyChunksSodiumOptionsApiCompat.register()) {
                LazyChunksMod.LOGGER.info("Registered LazyChunks directly in Sodium Options API video settings");
            }
        } catch (LinkageError error) {
            LazyChunksMod.LOGGER.warn("Failed to register LazyChunks in Sodium Options API video settings", error);
        }
    }
}
