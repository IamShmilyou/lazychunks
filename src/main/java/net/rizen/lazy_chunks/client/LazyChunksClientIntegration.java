package net.rizen.lazy_chunks.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.rizen.lazy_chunks.LazyChunksMod;

public final class LazyChunksClientIntegration {
    private static boolean sodiumOptionsPageRegistered;
    private static boolean embeddiumOptionsPageRegistered;

    private LazyChunksClientIntegration() {
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(LazyChunksConfigScreen::create)
        );
    }

    public static void registerOptionalSodiumOptionsPage() {
        if (sodiumOptionsPageRegistered || !isSodiumOptionsApiLoaded()) {
            return;
        }

        try {
            LazyChunksSodiumOptionsIntegration.register();
            sodiumOptionsPageRegistered = true;
            LazyChunksMod.LOGGER.info("Registered LazyChunks Sodium/Embeddium options page");
        } catch (LinkageError | RuntimeException e) {
            LazyChunksMod.LOGGER.warn("Unable to register LazyChunks Sodium/Embeddium options page", e);
        }
    }

    public static void registerOptionalEmbeddiumOptionsPage() {
        if (embeddiumOptionsPageRegistered || isSodiumOptionsApiLoaded() || !isEmbeddiumOptionsApiLoaded()) {
            return;
        }

        try {
            LazyChunksEmbeddiumOptionsIntegration.register();
            embeddiumOptionsPageRegistered = true;
            LazyChunksMod.LOGGER.info("Registered LazyChunks Embeddium options page");
        } catch (LinkageError | RuntimeException e) {
            LazyChunksMod.LOGGER.warn("Unable to register LazyChunks Embeddium options page", e);
        }
    }

    public static boolean isSodiumVideoSettingsLoaded() {
        ModList modList = ModList.get();
        return modList.isLoaded("sodium") || modList.isLoaded("embeddium") || modList.isLoaded("rubidium");
    }

    public static boolean isSodiumOptionsApiLoaded() {
        return ModList.get().isLoaded("sodiumoptionsapi") && classExists("toni.sodiumoptionsapi.api.OptionGUIConstruction");
    }

    public static boolean isEmbeddiumOptionsApiLoaded() {
        return ModList.get().isLoaded("embeddium") && classExists("org.embeddedt.embeddium.api.OptionGUIConstructionEvent");
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, LazyChunksClientIntegration.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
