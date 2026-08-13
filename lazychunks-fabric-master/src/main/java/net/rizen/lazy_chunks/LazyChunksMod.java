package net.rizen.lazy_chunks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LazyChunksMod implements ModInitializer {
	public static final String MOD_ID = "lazy_chunks";
	public static final String VERSION = FabricLoader.getInstance()
			.getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("?");

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LazyChunksConfig config = LazyChunksConfig.getInstance();

		LOGGER.info("LazyChunks {} loaded", VERSION);
		LOGGER.info("Lazy Chunk Loading: {} (targetFps={}, fpsThreshold={}, baseWeight={})",
				config.lazyChunkLoadingEnabled ? "enabled" : "disabled",
				config.targetFps,
				config.fpsThreshold,
				config.baseWeightPerFrame);
	}
}
