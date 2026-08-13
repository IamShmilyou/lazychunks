package net.rizen.lazy_chunks.gui;

import net.fabricmc.loader.api.FabricLoader;

public final class SodiumCompat {
    private SodiumCompat() {
    }

    public static boolean isSodiumLoaded() {
        return FabricLoader.getInstance().isModLoaded("sodium");
    }

    public static boolean isSodiumOptionsApiLoaded() {
        return FabricLoader.getInstance().isModLoaded("sodiumoptionsapi");
    }
}
