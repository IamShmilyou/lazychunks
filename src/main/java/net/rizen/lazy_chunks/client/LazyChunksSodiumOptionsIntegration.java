package net.rizen.lazy_chunks.client;

import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import net.rizen.lazy_chunks.LazyChunksMod;
import toni.sodiumoptionsapi.api.ExtendedOptionGroup;
import toni.sodiumoptionsapi.api.OptionGUIConstruction;
import toni.sodiumoptionsapi.api.OptionIdentifier;
import toni.sodiumoptionsapi.util.IOptionGroupIdAccessor;

import java.util.List;

public final class LazyChunksSodiumOptionsIntegration {
    private static final OptionIdentifier<Void> PAGE_ID = OptionIdentifier.create(LazyChunksMod.MOD_ID, "settings");

    private LazyChunksSodiumOptionsIntegration() {
    }

    public static void register() {
        OptionGUIConstruction.EVENT.register(LazyChunksSodiumOptionsIntegration::addLazyChunksPage);
    }

    private static void addLazyChunksPage(List<me.jellysquid.mods.sodium.client.gui.options.OptionPage> pages) {
        OptionGroup.Builder groupBuilder = ExtendedOptionGroup.createBuilder(PAGE_ID);
        me.jellysquid.mods.sodium.client.gui.options.OptionPage page = new me.jellysquid.mods.sodium.client.gui.options.OptionPage(
                LazyChunksOptionsPageFactory.PAGE_NAME,
                LazyChunksOptionsPageFactory.buildGroups(groupBuilder)
        );
        ((IOptionGroupIdAccessor) page).sodiumOptionsAPI$setId(PAGE_ID);
        pages.add(page);
    }
}
