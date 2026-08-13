package net.rizen.lazy_chunks.gui;

import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import net.fabricmc.loader.api.FabricLoader;
import net.rizen.lazy_chunks.LazyChunksMod;
import toni.sodiumoptionsapi.api.OptionGUIConstruction;
import toni.sodiumoptionsapi.api.OptionIdentifier;
import toni.sodiumoptionsapi.util.IOptionGroupIdAccessor;

import java.util.List;

public final class SodiumOptionsApiCompat {
    private static final String API_MOD_ID = "sodiumoptionsapi";
    private static boolean registered;

    private SodiumOptionsApiCompat() {
    }

    public static void registerConfigPage() {
        if (registered || !FabricLoader.getInstance().isModLoaded(API_MOD_ID)) {
            return;
        }

        registered = true;
        OptionGUIConstruction.EVENT.register(SodiumOptionsApiCompat::addConfigPage);
        LazyChunksMod.LOGGER.info("Registered LazyChunks options with Sodium Options API");
    }

    private static void addConfigPage(List<OptionPage> pages) {
        for (OptionPage page : pages) {
            if ("LazyChunks".equals(page.getName().getString())) {
                return;
            }
        }

        OptionPage page = SodiumOptionsPageFactory.createPage();
        ((IOptionGroupIdAccessor) page).sodiumOptionsAPI$setId(OptionIdentifier.create(LazyChunksMod.MOD_ID, "config"));
        pages.add(page);
    }
}
