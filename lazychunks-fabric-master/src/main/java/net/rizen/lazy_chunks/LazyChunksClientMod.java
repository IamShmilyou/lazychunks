package net.rizen.lazy_chunks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.rizen.lazy_chunks.compat.sodium.LazyChunksSodiumOptionsApiCompat;

public class LazyChunksClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("sodiumoptionsapi")) {
            return;
        }

        try {
            LazyChunksSodiumOptionsApiCompat.register();
            LazyChunksMod.LOGGER.info("Registered LazyChunks directly in Sodium Options API video settings");
        } catch (LinkageError error) {
            LazyChunksMod.LOGGER.warn("Failed to register LazyChunks in Sodium Options API video settings", error);
        }
    }
}
