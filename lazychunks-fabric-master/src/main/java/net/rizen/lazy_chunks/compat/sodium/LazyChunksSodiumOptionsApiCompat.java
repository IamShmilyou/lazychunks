package net.rizen.lazy_chunks.compat.sodium;

import toni.sodiumoptionsapi.api.OptionGUIConstruction;

public final class LazyChunksSodiumOptionsApiCompat {
    private static boolean registered;

    private LazyChunksSodiumOptionsApiCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        OptionGUIConstruction.EVENT.register(pages -> pages.add(LazyChunksLegacySodiumPage.create()));
    }
}
