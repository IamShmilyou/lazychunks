package net.rizen.lazy_chunks.client;

import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import net.rizen.lazy_chunks.LazyChunksMod;
import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;
import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;

public final class LazyChunksEmbeddiumOptionsIntegration {
    private static final OptionIdentifier<Void> PAGE_ID = OptionIdentifier.create(LazyChunksMod.MOD_ID, "settings");

    private LazyChunksEmbeddiumOptionsIntegration() {
    }

    public static void register() {
        OptionGUIConstructionEvent.BUS.addListener(LazyChunksEmbeddiumOptionsIntegration::addLazyChunksPage);
    }

    private static void addLazyChunksPage(OptionGUIConstructionEvent event) {
        OptionGroup.Builder groupBuilder = OptionGroup.createBuilder()
                .setId(LazyChunksOptionsPageFactory.id("settings"));

        event.addPage(new OptionPage(
                PAGE_ID,
                LazyChunksOptionsPageFactory.PAGE_NAME,
                LazyChunksOptionsPageFactory.buildGroups(groupBuilder)
        ));
    }
}
