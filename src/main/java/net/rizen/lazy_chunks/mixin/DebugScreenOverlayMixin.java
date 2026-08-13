package net.rizen.lazy_chunks.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.rizen.lazy_chunks.LazyChunksMod;
import net.rizen.lazy_chunks.config.LazyChunksConfig;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.TeleportDetector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.RandomAccess;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private static volatile long lazychunks$lastUpdateTimeNanos = 0L;
    @Unique
    private static final long UPDATE_INTERVAL_NANOS = 1_500_000_000L;
    @Unique
    private static volatile List<String> lazychunks$cachedDebugBlock = null;

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V",
                    ordinal = 0
            )
            ,
            index = 1
    )
    private List<String> lazychunks$injectDebugInfo(List<String> lines) {
        if (!lazychunks$shouldInjectDebugInfo()) {
            return lines;
        }

        return new LazyChunksLineView(lazychunks$getDebugBlock(), lines);
    }

    @Unique
    private boolean lazychunks$shouldInjectDebugInfo() {
        return this.minecraft != null
                && LazyChunksConfig.getInstance().showDebugOverlayInfo
                && this.minecraft.debugEntries != null
                && this.minecraft.debugEntries.isOverlayVisible();
    }

    @Unique
    private static List<String> lazychunks$getDebugBlock() {
        long now = System.nanoTime();
        List<String> cached = lazychunks$cachedDebugBlock;
        if (cached == null || now - lazychunks$lastUpdateTimeNanos >= UPDATE_INTERVAL_NANOS) {
            cached = lazychunks$buildDebugInfo();
            lazychunks$cachedDebugBlock = cached;
            lazychunks$lastUpdateTimeNanos = now;
        }
        return cached;
    }

    @Unique
    private static List<String> lazychunks$buildDebugInfo() {
        LazyChunksConfig config = LazyChunksConfig.getInstance();

        List<String> info = new ArrayList<>();
        info.add(ChatFormatting.YELLOW.toString() + ChatFormatting.BOLD + "LazyChunks " + LazyChunksMod.VERSION);

        if (config.lazyChunkLoadingEnabled) {
            info.add(String.format(Locale.ROOT, "Pending: %d | Weight: %.1f | Budget: %.1f",
                    LazyChunkLoading.getLastPendingTasks(),
                    LazyChunkLoading.getLastWeight(),
                    LazyChunkLoading.getLastBudget()));

            info.add(String.format(Locale.ROOT, "1%% Low FPS: %d | Process: %.2fms",
                    Math.round(LazyChunkLoading.getOnePercentLowFps()),
                    LazyChunkLoading.getLastProcessingTimeMs()));

            if (LazyChunkLoading.getLastAdaptiveLowFpsSeconds() > 0) {
                info.add(String.format(Locale.ROOT, "Core: +%d | Low: %ds",
                        LazyChunkLoading.getLastAdaptiveBoostCores(),
                        Math.round(LazyChunkLoading.getLastAdaptiveLowFpsSeconds())));
            }

            StringBuilder status = new StringBuilder();
            if (LazyChunkLoading.wasThrottled()) {
                status.append(ChatFormatting.GREEN).append("Throttling");
            } else {
                status.append(ChatFormatting.GRAY).append("Idle");
            }

            if (LazyChunkLoading.getQueueGrowthRate() > 5) {
                status.append(ChatFormatting.GOLD).append(" [Queue+]");
            }

            if (TeleportDetector.isTeleportRecovery()) {
                status.append(ChatFormatting.RED).append(" [Teleport]");
            }

            info.add(status.toString());
        } else {
            info.add(ChatFormatting.GRAY + "Disabled");
        }

        info.add("");
        return List.copyOf(info);
    }

    private static final class LazyChunksLineView extends AbstractList<String> implements RandomAccess {
        private final List<String> prefix;
        private final List<String> base;
        private final int prefixSize;

        private LazyChunksLineView(List<String> prefix, List<String> base) {
            this.prefix = prefix;
            this.base = base;
            this.prefixSize = prefix.size();
        }

        @Override
        public String get(int index) {
            if (index < 0 || index >= size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
            }

            if (index < this.prefixSize) {
                return this.prefix.get(index);
            }

            return this.base.get(index - this.prefixSize);
        }

        @Override
        public int size() {
            return this.prefixSize + this.base.size();
        }
    }
}
