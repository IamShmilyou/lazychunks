package net.rizen.lazy_chunks;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.rizen.lazy_chunks.client.LazyChunksClientIntegration;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LazyChunksMod.MOD_ID)
public class LazyChunksMod {
	public static final String MOD_ID = "lazy_chunks";
	public static final String VERSION = ModList.get()
			.getModContainerById(MOD_ID)
			.map(container -> container.getModInfo().getVersion().toString())
			.orElse("?");

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public LazyChunksMod() {
		LazyChunksConfig config = LazyChunksConfig.getInstance();
		LazyChunksClientIntegration.registerConfigScreen();
		LazyChunksClientIntegration.registerOptionalSodiumOptionsPage();
		LazyChunksClientIntegration.registerOptionalEmbeddiumOptionsPage();

		LOGGER.info("LazyChunks {} loaded", VERSION);
		LOGGER.info("Lazy Chunk Loading: {} (targetFps={}, fpsThreshold={}, baseWeight={})",
				config.lazyChunkLoadingEnabled ? "enabled" : "disabled",
				config.targetFps,
				config.fpsThreshold,
				config.baseWeightPerFrame);
	}
}
