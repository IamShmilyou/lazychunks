package net.rizen.lazy_chunks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = LazyChunksMod.MOD_ID, dist = Dist.CLIENT)
public class LazyChunksMod {
	public static final String MOD_ID = "lazy_chunks";
	public static final String VERSION = "2.0+mc1.21.1-neoforge";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public LazyChunksMod() {
		LazyChunksConfig config = LazyChunksConfig.getInstance();
		LazyChunksClientMod.init();

		LOGGER.info("LazyChunks {} loaded", VERSION);
		LOGGER.info("Lazy Chunk Loading: {} (targetFps={}, fpsThreshold={}, baseWeight={})",
				config.lazyChunkLoadingEnabled ? "enabled" : "disabled",
				config.targetFps,
				config.fpsThreshold,
				config.baseWeightPerFrame);
	}
}
